package com.sahha.clinical.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
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
import tools.jackson.databind.json.JsonMapper;
import com.sahha.clinical.client.communication.CommunicationCareAccessClient;
import com.sahha.clinical.client.communication.CommunicationShareAccessClient;
import com.sahha.clinical.client.scheduling.SchedulingClinicalContextClient;
import com.sahha.clinical.entity.*;
import com.sahha.clinical.exception.*;
import com.sahha.clinical.repository.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
class SharedCareClinicalHttpIntegrationTests {
    private static final String TOKEN = "shared.care.token", CSRF = "synthetic-care-csrf";
    private final Instant now = Instant.now();
    private final UUID org = UUID.randomUUID(), patient = UUID.randomUUID(), globalPatient = UUID.randomUUID(),
            owner = UUID.randomUUID(), member = UUID.randomUUID(), actor = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired ConsultationRepository records;
    @Autowired ClinicalDiagnosisRepository diagnoses;
    @Autowired ClinicalCorrectionRepository corrections;
    @Autowired ClinicalAccessAuditEventRepository audits;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CommunicationCareAccessClient care;
    @MockitoBean CommunicationShareAccessClient selections;
    @MockitoBean SchedulingClinicalContextClient scheduling;
    @MockitoBean Clock clock;

    @BeforeEach void setup() {
        workspace(org, actor, List.of("DOCTOR")); when(clock.instant()).thenReturn(now);
        when(care.requireCare(any(), any(), any(), anyString(), anyString())).thenThrow(new ConsultationNotFoundException());
        when(selections.requireGrant(any(), anyString(), any(), any(), any(), anyString(), anyString()))
                .thenThrow(new ConsultationNotFoundException());
    }
    @Test void discoversAllSamePatientFinalisedAuthorsButNoDraftsOtherPatientsOrOrganisations() throws Exception {
        var older = record(org, patient, owner, true, 1);
        var newer = record(org, patient, UUID.randomUUID(), true, 2);
        record(org, patient, owner, false, 3); record(org, UUID.randomUUID(), owner, true, 4);
        record(UUID.randomUUID(), patient, owner, true, 5); allow();
        mvc.perform(get(path()).cookie(access()).param("size", "1").header("X-Request-ID", "care-history"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.organisationId").value(org.toString()))
                .andExpect(jsonPath("$.patientRegistrationId").value(patient.toString()))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].consultationId").value(newer.getId().toString()))
                .andExpect(jsonPath("$.content[0].additionalNotes").doesNotExist())
                .andExpect(jsonPath("$.content[0].reasonForConsultation").doesNotExist());
        mvc.perform(get(path()).cookie(access()).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].consultationId").value(older.getId().toString()));
        verify(care).requireCare(org, actor, patient, TOKEN, "care-history");
        assertEquals(2, audits.countByResourceTypeAndResourceId("CARE_HISTORY", patient));
        verifyNoInteractions(selections, scheduling);
    }
    @Test void readsFinalisedHistoryAndCorrectionsWithoutChangingOriginalOrGrantingAuthorWrites() throws Exception {
        var record = record(org, patient, owner, false, 1);
        var diagnosis = diagnoses.saveAndFlush(ClinicalDiagnosis.create(record.getId(), 0, null, null,
                "Synthetic original diagnosis", DiagnosisType.PRIMARY, DiagnosisStatus.CONFIRMED, null));
        record.finalizeRecord(owner, Clock.fixed(now, ZoneOffset.UTC)); records.saveAndFlush(record);
        corrections.saveAndFlush(ClinicalCorrection.create(record, owner, CorrectionTargetType.DIAGNOSIS,
                diagnosis.getId(), "label", "Synthetic original diagnosis", "Synthetic corrected diagnosis",
                "Synthetic correction reason", record.getVersion() + 1, now)); allow();
        mvc.perform(get(path() + "/" + record.getId()).cookie(access()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.consultation.status").value("FINALIZED"))
                .andExpect(jsonPath("$.consultation.diagnoses[0].label").value("Synthetic corrected diagnosis"))
                .andExpect(jsonPath("$.consultation.corrections[0].actorUserId").value(owner.toString()))
                .andExpect(jsonPath("$.consultation.corrections[0].oldValue").value("Synthetic original diagnosis"));
        assertEquals("Synthetic original diagnosis", diagnoses.findById(diagnosis.getId()).orElseThrow().getLabel());
        mvc.perform(get("/api/v1/consultations/{id}/record", record.getId()).cookie(access())).andExpect(status().isNotFound());
        var mapper = JsonMapper.builder().build();
        mvc.perform(patch("/api/v1/consultations/{id}/draft", record.getId()).cookie(access(), csrf()).header("X-XSRF-TOKEN", CSRF)
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("version", record.getVersion(),
                        "reasonForConsultation", "Synthetic unauthorized rewrite")))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/consultations/{id}/corrections", record.getId()).cookie(access(), csrf()).header("X-XSRF-TOKEN", CSRF)
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("version", record.getVersion(),
                        "targetType", "DIAGNOSIS", "targetId", diagnosis.getId(), "fieldName", "label",
                        "newValue", "Unauthorized correction", "reason", "Synthetic reason")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/clinical/shared/{patient}/CONSULTATION/{id}", patient, record.getId()).cookie(access()))
                .andExpect(status().isNotFound()); // No implicit expansion of selected sharing.
    }
    @Test void wrongPatientOrganisationAndDraftDoNotReachCareAuthority() throws Exception {
        var draft = record(org, patient, owner, false, 1);
        var foreignPatient = record(org, UUID.randomUUID(), owner, true, 2);
        var foreignOrg = record(UUID.randomUUID(), patient, owner, true, 3);
        allow();
        for (var record : List.of(draft, foreignPatient, foreignOrg)) {
            mvc.perform(get(path() + "/" + record.getId()).cookie(access())).andExpect(status().isNotFound());
        }
        verifyNoInteractions(care);
    }
    @Test void revocationExpiryAndOutageStopNewReadsEvenAfterDiscovery() throws Exception {
        var record = record(org, patient, owner, true, 1); allow();
        mvc.perform(get(path()).cookie(access())).andExpect(status().isOk());
        mvc.perform(get(path() + "/" + record.getId()).cookie(access())).andExpect(status().isOk());
        for (RuntimeException denial : List.of(new ConsultationNotFoundException(), new SharingContextUnavailableException())) {
            doThrow(denial).when(care).requireCare(eq(org), eq(actor), eq(patient), eq(TOKEN), anyString());
            int expected = denial instanceof SharingContextUnavailableException ? 503 : 404;
            mvc.perform(get(path()).cookie(access())).andExpect(status().is(expected));
            mvc.perform(get(path() + "/" + record.getId()).cookie(access())).andExpect(status().is(expected));
        }
        doReturn(now).when(care).requireCare(eq(org), eq(actor), eq(patient), eq(TOKEN), anyString());
        mvc.perform(get(path()).cookie(access())).andExpect(status().isNotFound());
        mvc.perform(get(path() + "/" + record.getId()).cookie(access())).andExpect(status().isNotFound());
        assertEquals(4, audits.countByResourceTypeAndResourceId("CARE_HISTORY", patient));
        assertEquals(4, audits.countByResourceTypeAndResourceId("CONSULTATION", record.getId()));
        verifyNoInteractions(selections, scheduling);
    }
    @Test void expiredDuringAssemblyDoesNotReturnRecord() throws Exception {
        var record = record(org, patient, owner, true, 1); allow();
        when(clock.instant()).thenReturn(now.plusSeconds(300));
        mvc.perform(get(path() + "/" + record.getId()).cookie(access())).andExpect(status().isNotFound());
    }
    @Test void absentCareUnrelatedDoctorOrDifferentActiveOrganisationCannotListHistory() throws Exception {
        record(org, patient, owner, true, 1);
        mvc.perform(get(path()).cookie(access())).andExpect(status().isNotFound());
        allow(); workspace(org, UUID.randomUUID(), List.of("DOCTOR"));
        mvc.perform(get(path()).cookie(access())).andExpect(status().isNotFound());
        workspace(UUID.randomUUID(), actor, List.of("DOCTOR"));
        mvc.perform(get(path()).cookie(access())).andExpect(status().isNotFound());
    }
    @Test void onlyExplicitDoctorsMayRequestCareReadsAndWritesOnNewRouteAreDenied() throws Exception {
        for (String role : List.of("RECEPTIONIST", "ORGANIZATION_ADMIN", "PLATFORM_ADMIN")) {
            workspace(org, actor, List.of(role));
            mvc.perform(get(path()).cookie(access())).andExpect(status().isForbidden());
            mvc.perform(get(path() + "/" + UUID.randomUUID()).cookie(access())).andExpect(status().isForbidden());
        }
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        workspace(org, actor, List.of("DOCTOR"));
        mvc.perform(post(path()).cookie(access(), csrf()).header("X-XSRF-TOKEN", CSRF)).andExpect(status().isForbidden());
        verifyNoInteractions(care);
    }
    @Test void boundedPaginationRejectsInvalidRequestsBeforeQueryingAuthority() throws Exception {
        for (String page : List.of("-1", "invalid")) mvc.perform(get(path()).cookie(access()).param("page", page)).andExpect(status().isBadRequest());
        for (String size : List.of("0", "51")) mvc.perform(get(path()).cookie(access()).param("size", size)).andExpect(status().isBadRequest());
        verifyNoInteractions(care);
    }
    @Test void attachmentContextIsMinimalLiveAndNeverGrantsAuthorAttachmentAuthority() throws Exception {
        var record = record(org, patient, owner, true, 1); allow();
        String context = path() + "/" + record.getId() + "/attachment-context";
        mvc.perform(get(context).cookie(access()).header("X-Request-ID", "care-attachments"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.consultationId").value(record.getId().toString()))
                .andExpect(jsonPath("$.patientId").value(globalPatient.toString()))
                .andExpect(jsonPath("$.actorUserId").value(actor.toString()))
                .andExpect(jsonPath("$.consultationStatus").value("FINALIZED"))
                .andExpect(jsonPath("$.reasonForConsultation").doesNotExist())
                .andExpect(jsonPath("$.consultation").doesNotExist());
        mvc.perform(get("/api/v1/internal/clinical/consultations/{id}/attachment-context", record.getId()).cookie(access()))
                .andExpect(status().isNotFound());
        for (RuntimeException denial : List.of(new ConsultationNotFoundException(), new SharingContextUnavailableException())) {
            doThrow(denial).when(care).requireCare(eq(org), eq(actor), eq(patient), eq(TOKEN), anyString());
            mvc.perform(get(context).cookie(access())).andExpect(status().is(denial instanceof SharingContextUnavailableException ? 503 : 404));
        }
        allow(); when(clock.instant()).thenReturn(now.plusSeconds(300));
        mvc.perform(get(context).cookie(access())).andExpect(status().isNotFound());
        verifyNoInteractions(selections, scheduling);
    }

    @Test void attachmentContextRejectsDraftForeignPatientOrgAndAdministrativeRoles() throws Exception {
        allow();
        for (var record : List.of(record(org, patient, owner, false, 1),
                record(org, UUID.randomUUID(), owner, true, 2), record(UUID.randomUUID(), patient, owner, true, 3))) {
            mvc.perform(get(path() + "/" + record.getId() + "/attachment-context").cookie(access())).andExpect(status().isNotFound());
        }
        for (String role : List.of("RECEPTIONIST", "ORGANIZATION_ADMIN", "PLATFORM_ADMIN")) {
            workspace(org, actor, List.of(role));
            mvc.perform(get(path() + "/" + UUID.randomUUID() + "/attachment-context").cookie(access())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(care);
    }

    private Consultation record(UUID organisation, UUID registration, UUID author, boolean finalized, int seconds) {
        var time = Clock.fixed(now.minusSeconds(60).plusSeconds(seconds), ZoneOffset.UTC);
        var value = Consultation.draft(organisation, UUID.randomUUID(), registration, globalPatient, author, member, time);
        value.replaceNarrativeDraft("Synthetic visit", "Synthetic assessment", "Synthetic plan", null, "Synthetic private note", time);
        records.saveAndFlush(value);
        if (finalized) { value.finalizeRecord(author, time); records.saveAndFlush(value); }
        return value;
    }
    private void allow() { doReturn(now.plusSeconds(300)).when(care).requireCare(eq(org), eq(actor), eq(patient), eq(TOKEN), anyString()); }
    private String path() { return "/api/v1/clinical/shared-care/" + patient + "/consultations"; }
    private Cookie access() { return new Cookie("SAHHA_ACCESS_TOKEN", TOKEN); }
    private Cookie csrf() { return new Cookie("XSRF-TOKEN", CSRF); }
    private void workspace(UUID organisation, UUID user, List<String> roles) {
        when(decoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "RS256").subject(user.toString())
                .issuer("http://localhost:8081").audience(List.of("sahha-api")).issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("sid", UUID.randomUUID().toString()).claim("cv", 1).claim("roles", List.of())
                .claim("org_id", organisation.toString()).claim("org_roles", roles).claim("token_type", "access").build());
    }
}
