package com.sahha.clinical.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.clinical.client.organisation.OrganisationDoctorClient;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.exception.*;
import com.sahha.clinical.repository.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReferralSourceHttpIntegrationTests {
    private static final String TOKEN = "source.doctor.token", PATH = "/api/v1/consultations/referral-sources";
    private final UUID org = UUID.randomUUID(), actor = UUID.randomUUID(), member = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired ConsultationRepository consultations;
    @Autowired ClinicalAccessAuditEventRepository audits;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean OrganisationDoctorClient doctors;
    @BeforeEach void setup() {
        workspace(org, List.of("DOCTOR"));
        when(doctors.requireCurrentMembership(eq(org), eq(actor), eq(TOKEN), anyString())).thenReturn(member);
    }
    @Test void listsOnlyOwnFinalisedRecordsUnderTheOriginalCurrentMembershipAndAuditsEachRead() throws Exception {
        var own = record(org, actor, member, true, 1);
        record(org, actor, member, false, 2);
        record(UUID.randomUUID(), actor, member, true, 3);
        record(org, UUID.randomUUID(), member, true, 4);
        record(org, actor, UUID.randomUUID(), true, 5);
        mvc.perform(get(PATH).cookie(access()).header("X-Request-ID", "sources-test"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].consultationId").value(own.getId().toString()))
                .andExpect(jsonPath("$.content[0].patientRegistrationId").value(own.getPatientRegistrationId().toString()))
                .andExpect(jsonPath("$.content[0].patientId").doesNotExist())
                .andExpect(jsonPath("$.content[0].reasonForConsultation").doesNotExist())
                .andExpect(jsonPath("$.content[0].additionalNotes").doesNotExist());
        assertEquals(1, audits.countByResourceTypeAndResourceId("CONSULTATION", own.getId()));
        verify(doctors).requireCurrentMembership(org, actor, TOKEN, "sources-test");
    }
    @Test void pagesNewestFirstAndIgnoresCallerSuppliedOwnershipOverrides() throws Exception {
        var older = record(org, actor, member, true, 1);
        record(org, actor, member, true, 2);
        mvc.perform(get(PATH).param("page", "1").param("size", "1")
                .param("doctorUserId", UUID.randomUUID().toString()).param("organisationId", UUID.randomUUID().toString()).cookie(access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content[0].consultationId").value(older.getId().toString()));
    }
    @Test void invalidPaginationFailsBeforeOrganisationLookup() throws Exception {
        for (String query : List.of("?page=-1", "?page=10001", "?page=bad", "?size=0", "?size=51")) {
            mvc.perform(get(PATH + query).cookie(access())).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(doctors);
    }
    @Test void administrativeRolesAndAnonymousRequestsHaveNoClinicalDiscoveryAccess() throws Exception {
        for (String role : List.of("RECEPTIONIST", "ORGANIZATION_ADMIN", "PLATFORM_ADMIN", "PATIENT")) {
            workspace(org, List.of(role));
            mvc.perform(get(PATH).cookie(access())).andExpect(status().isForbidden());
        }
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()); verifyNoInteractions(doctors);
    }
    @Test void suspendedMembershipAndUnavailableAuthorityFailClosed() throws Exception {
        record(org, actor, member, true, 1);
        when(doctors.requireCurrentMembership(eq(org), eq(actor), eq(TOKEN), anyString())).thenThrow(new ClinicalAccessDeniedException());
        mvc.perform(get(PATH).cookie(access())).andExpect(status().isForbidden()).andExpect(jsonPath("$.content").doesNotExist());
        when(doctors.requireCurrentMembership(eq(org), eq(actor), eq(TOKEN), anyString())).thenThrow(new OrganisationContextUnavailableException());
        mvc.perform(get(PATH).cookie(access())).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.content").doesNotExist());
    }
    private Consultation record(UUID organisation, UUID user, UUID membership, boolean finalized, int seconds) {
        var clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z").plusSeconds(seconds), ZoneOffset.UTC);
        var value = Consultation.draft(organisation, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), user, membership, clock);
        value.replaceNarrativeDraft("Synthetic visit", "Private synthetic assessment", "Synthetic plan", "Synthetic follow-up", "Private note", clock);
        value = consultations.saveAndFlush(value);
        if (finalized) { value.finalizeRecord(user, clock); value = consultations.saveAndFlush(value); }
        return value;
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
