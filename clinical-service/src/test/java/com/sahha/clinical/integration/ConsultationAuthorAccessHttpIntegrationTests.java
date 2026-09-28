package com.sahha.clinical.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.clinical.client.organisation.OrganisationDoctorClient;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.exception.OrganisationContextUnavailableException;
import com.sahha.clinical.repository.ConsultationRepository;
import com.sahha.clinical.repository.ClinicalAccessAuditEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsultationAuthorAccessHttpIntegrationTests {
    private static final String TOKEN = "author.access.token";
    private final UUID org = UUID.randomUUID(), actor = UUID.randomUUID(), member = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired ConsultationRepository consultations;
    @Autowired ClinicalAccessAuditEventRepository audits;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean OrganisationDoctorClient doctors;
    private Consultation record;

    @BeforeEach void setup() {
        record = consultations.saveAndFlush(Consultation.draft(org, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), actor, member, Clock.systemUTC()));
        workspace(org, List.of("DOCTOR"));
        when(doctors.requireCurrentMembership(eq(org), eq(actor), eq(TOKEN), anyString())).thenReturn(member);
    }

    @Test void authorReadsAreAuditedOnlyWithTheOriginalLiveMembership() throws Exception {
        for (String route : readRoutes()) {
            mvc.perform(get(route).cookie(access()).header("X-Request-ID", "author-live-check"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        }
        // Three author decisions and the existing structured-record read audit.
        assertEquals(4, audits.countByResourceTypeAndResourceId("CONSULTATION", record.getId()));
        verify(doctors, times(3)).requireCurrentMembership(org, actor, TOKEN, "author-live-check");
    }

    @Test void suspensionDeniesRecordAndAttachmentReadsDespiteAnUnexpiredDoctorToken() throws Exception {
        when(doctors.requireCurrentMembership(eq(org), eq(actor), eq(TOKEN), anyString()))
                .thenThrow(new ClinicalAccessDeniedException());
        for (String route : readRoutes()) {
            mvc.perform(get(route).cookie(access())).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.patientId").doesNotExist());
        }
        assertEquals(3, audits.countByResourceTypeAndResourceId("CONSULTATION", record.getId()));
    }

    @Test void replacementMembershipDoesNotInheritHistoricalAuthorship() throws Exception {
        when(doctors.requireCurrentMembership(eq(org), eq(actor), eq(TOKEN), anyString())).thenReturn(UUID.randomUUID());
        mvc.perform(get(readRoutes().getFirst()).cookie(access())).andExpect(status().isForbidden());
    }

    @Test void membershipAuthorityOutageDeniesAccessAndRecordsTheDenial() throws Exception {
        when(doctors.requireCurrentMembership(eq(org), eq(actor), eq(TOKEN), anyString()))
                .thenThrow(new OrganisationContextUnavailableException());
        mvc.perform(get(readRoutes().getFirst()).cookie(access())).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.doctorUserId").doesNotExist());
        assertEquals(1, audits.countByResourceTypeAndResourceId("CONSULTATION", record.getId()));
    }

    @Test void suspendedAuthorCannotMutateDraftFinalizeOrAppendCorrections() throws Exception {
        when(doctors.requireCurrentMembership(eq(org), eq(actor), eq(TOKEN), anyString()))
                .thenThrow(new ClinicalAccessDeniedException());
        String base = "/api/v1/consultations/" + record.getId();
        for (var request : List.of(
                patch(base + "/draft").content("{\"version\":0,\"reasonForConsultation\":\"Synthetic reason\",\"draftNotes\":\"Synthetic note\"}"),
                put(base + "/draft-content").content("{\"version\":0,\"reasonForConsultation\":\"Synthetic reason\",\"symptoms\":[],\"history\":[],\"examinationFindings\":[],\"diagnoses\":[],\"medications\":[]}"),
                post(base + "/finalize").content("{\"version\":0}"),
                post(base + "/corrections").content("{\"version\":0,\"targetType\":\"CONSULTATION\",\"fieldName\":\"reasonForConsultation\",\"newValue\":\"Synthetic correction\",\"reason\":\"Synthetic correction reason\"}"))) {
            mvc.perform(request.cookie(access(), new Cookie("XSRF-TOKEN", "author-csrf"))
                    .header("X-XSRF-TOKEN", "author-csrf").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
        assertEquals(0L, consultations.findById(record.getId()).orElseThrow().getVersion());
        assertEquals(4, audits.countByResourceTypeAndResourceId("CONSULTATION", record.getId()));
    }

    @Test void crossOrganisationAndAdministrativeCallersNeverReachMembershipAuthority() throws Exception {
        workspace(UUID.randomUUID(), List.of("DOCTOR"));
        mvc.perform(get(readRoutes().getFirst()).cookie(access())).andExpect(status().isNotFound());
        for (String role : List.of("RECEPTIONIST", "ORGANIZATION_ADMIN", "PLATFORM_ADMIN")) {
            workspace(org, List.of(role));
            mvc.perform(get(readRoutes().getFirst()).cookie(access())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(doctors);
    }

    private List<String> readRoutes() {
        return List.of("/api/v1/consultations/" + record.getId(),
                "/api/v1/consultations/" + record.getId() + "/record",
                "/api/v1/internal/clinical/consultations/" + record.getId() + "/attachment-context");
    }
    private Cookie access() { return new Cookie("SAHHA_ACCESS_TOKEN", TOKEN); }
    private void workspace(UUID organisation, List<String> roles) {
        when(decoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "RS256")
                .subject(actor.toString()).issuer("http://localhost:8081").audience(List.of("sahha-api"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("sid", UUID.randomUUID().toString()).claim("cv", 1).claim("roles", List.of())
                .claim("org_id", organisation.toString()).claim("org_roles", roles).claim("token_type", "access").build());
    }
}
