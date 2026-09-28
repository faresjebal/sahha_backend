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
import com.sahha.clinical.client.communication.CommunicationShareAccessClient;
import com.sahha.clinical.entity.*;
import com.sahha.clinical.exception.*;
import com.sahha.clinical.repository.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedClinicalResourceHttpIntegrationTests {
    private static final String TOKEN = "shared.recipient.token";
    private final UUID organisation = UUID.randomUUID(), patient = UUID.randomUUID(),
            owner = UUID.randomUUID(), membership = UUID.randomUUID(), recipient = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired ConsultationRepository consultations;
    @Autowired ClinicalDiagnosisRepository diagnoses;
    @Autowired ClinicalMedicationItemRepository medications;
    @Autowired ClinicalHistoryEntryRepository history;
    @Autowired ClinicalCorrectionRepository corrections;
    @Autowired ClinicalAccessAuditEventRepository audits;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CommunicationShareAccessClient sharing;

    @BeforeEach void setup() {
        workspace(organisation, List.of("DOCTOR"));
        when(sharing.requireGrant(any(), anyString(), any(), any(), any(), anyString(), anyString()))
                .thenThrow(new ConsultationNotFoundException());
    }

    @Test void selectedChildReturnsOnlyItsEffectiveContentAndCannotOpenTheWholeRecord() throws Exception {
        var consultation = draft();
        var diagnosis = diagnoses.saveAndFlush(ClinicalDiagnosis.create(consultation.getId(), 0,
                null, null, "Synthetic diagnosis", DiagnosisType.PRIMARY, DiagnosisStatus.CONFIRMED, null));
        finalizeRecord(consultation);
        corrections.saveAndFlush(ClinicalCorrection.create(consultation, owner, CorrectionTargetType.DIAGNOSIS,
                diagnosis.getId(), "label", "Synthetic diagnosis", "Corrected synthetic diagnosis",
                "Synthetic correction", consultation.getVersion() + 1, Instant.now()));
        allow("DIAGNOSIS", diagnosis.getId());
        mvc.perform(get(path("DIAGNOSIS", diagnosis.getId())).cookie(access()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.diagnosis.label").value("Corrected synthetic diagnosis"))
                .andExpect(jsonPath("$.consultation").doesNotExist())
                .andExpect(jsonPath("$.medication").doesNotExist())
                .andExpect(jsonPath("$.allergy").doesNotExist())
                .andExpect(jsonPath("$.corrections").doesNotExist());
        mvc.perform(get(path("CONSULTATION", consultation.getId())).cookie(access()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/consultations/{id}/record", consultation.getId()).cookie(access()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/consultations/{id}/draft", consultation.getId())
                        .cookie(access(), new Cookie("XSRF-TOKEN", "shared-csrf"))
                        .header("X-XSRF-TOKEN", "shared-csrf").contentType("application/json")
                        .content("{\"version\":1,\"reasonForConsultation\":\"unauthorised edit\"}"))
                .andExpect(status().isNotFound());
        verify(sharing).requireGrant(eq(patient), eq("DIAGNOSIS"), eq(diagnosis.getId()),
                eq(owner), eq(membership), eq(TOKEN), anyString());
        assertEquals(1, audits.countByResourceTypeAndResourceId("DIAGNOSIS", diagnosis.getId()));
    }

    @Test void consultationMedicationAndAllergyAreExplicitlySelectedAndMedicalHistoryIsNotAnAllergy() throws Exception {
        var consultation = draft();
        var medication = medications.saveAndFlush(ClinicalMedicationItem.create(consultation.getId(), 0,
                MedicationKind.PRESCRIBED, "Synthetic medicine", null, null, "Synthetic dosage",
                null, null, null, null, null));
        var allergy = history.saveAndFlush(ClinicalHistoryEntry.create(consultation.getId(), 0,
                ClinicalHistoryCategory.ALLERGY, "Synthetic allergy", null));
        var medical = history.saveAndFlush(ClinicalHistoryEntry.create(consultation.getId(), 1,
                ClinicalHistoryCategory.MEDICAL, "Unselected private history", null));
        finalizeRecord(consultation);
        allow("CONSULTATION", consultation.getId()); allow("MEDICATION", medication.getId());
        allow("ALLERGY", allergy.getId()); allow("ALLERGY", medical.getId());
        mvc.perform(get(path("CONSULTATION", consultation.getId())).cookie(access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consultation.id").value(consultation.getId().toString()));
        mvc.perform(get(path("MEDICATION", medication.getId())).cookie(access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.medication.id").value(medication.getId().toString()))
                .andExpect(jsonPath("$.consultation").doesNotExist()).andExpect(jsonPath("$.allergy").doesNotExist());
        mvc.perform(get(path("ALLERGY", allergy.getId())).cookie(access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allergy.category").value("ALLERGY"))
                .andExpect(jsonPath("$.consultation").doesNotExist());
        mvc.perform(get(path("ALLERGY", medical.getId())).cookie(access())).andExpect(status().isNotFound());
    }

    @Test void wrongPatientOrganisationTypeAndDraftNeverReachSharingAuthority() throws Exception {
        var consultation = draft();
        mvc.perform(get(path("CONSULTATION", consultation.getId())).cookie(access())).andExpect(status().isNotFound());
        finalizeRecord(consultation);
        mvc.perform(get("/api/v1/clinical/shared/{patient}/CONSULTATION/{id}", UUID.randomUUID(), consultation.getId())
                .cookie(access())).andExpect(status().isNotFound());
        mvc.perform(get(path("MEDICATION", consultation.getId())).cookie(access())).andExpect(status().isNotFound());
        workspace(UUID.randomUUID(), List.of("DOCTOR"));
        mvc.perform(get(path("CONSULTATION", consultation.getId())).cookie(access())).andExpect(status().isNotFound());
        verifyNoInteractions(sharing);
    }

    @Test void revokedOrUnavailableSharingNeverFallsBackToAnotherReadPath() throws Exception {
        var consultation = draft(); finalizeRecord(consultation); allow("CONSULTATION", consultation.getId());
        mvc.perform(get(path("CONSULTATION", consultation.getId())).cookie(access())).andExpect(status().isOk());
        doThrow(new ConsultationNotFoundException()).when(sharing).requireGrant(eq(patient), eq("CONSULTATION"),
                eq(consultation.getId()), eq(owner), eq(membership), eq(TOKEN), anyString());
        mvc.perform(get(path("CONSULTATION", consultation.getId())).cookie(access())).andExpect(status().isNotFound());
        doThrow(new SharingContextUnavailableException()).when(sharing).requireGrant(eq(patient), eq("CONSULTATION"),
                eq(consultation.getId()), eq(owner), eq(membership), eq(TOKEN), anyString());
        mvc.perform(get(path("CONSULTATION", consultation.getId())).cookie(access())).andExpect(status().isServiceUnavailable());
        assertEquals(3, audits.countByResourceTypeAndResourceId("CONSULTATION", consultation.getId()));
    }

    @Test void administratorsReceptionistsAndAnonymousRequestsCannotReadSharedClinicalContent() throws Exception {
        for (String role : List.of("RECEPTIONIST", "ORGANIZATION_ADMIN", "PLATFORM_ADMIN")) {
            workspace(organisation, List.of(role));
            mvc.perform(get(path("CONSULTATION", UUID.randomUUID())).cookie(access())).andExpect(status().isForbidden());
        }
        mvc.perform(get(path("CONSULTATION", UUID.randomUUID()))).andExpect(status().isUnauthorized());
        verifyNoInteractions(sharing);
    }

    private Consultation draft() {
        var value = Consultation.draft(organisation, UUID.randomUUID(), patient, UUID.randomUUID(), owner, membership, Clock.systemUTC());
        value.replaceNarrativeDraft("Synthetic visit", "Synthetic assessment", "Synthetic treatment", "Synthetic follow-up", "Private note", Clock.systemUTC());
        return consultations.saveAndFlush(value);
    }
    private void finalizeRecord(Consultation value) { value.finalizeRecord(owner, Clock.systemUTC()); consultations.saveAndFlush(value); }
    private void allow(String type, UUID id) {
        doReturn(Instant.now().plusSeconds(300)).when(sharing).requireGrant(eq(patient), eq(type), eq(id),
                eq(owner), eq(membership), eq(TOKEN), anyString());
    }
    private String path(String type, UUID id) { return "/api/v1/clinical/shared/" + patient + "/" + type + "/" + id; }
    private Cookie access() { return new Cookie("SAHHA_ACCESS_TOKEN", TOKEN); }
    private void workspace(UUID org, List<String> roles) {
        when(decoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "RS256")
                .subject(recipient.toString()).issuer("http://localhost:8081").audience(List.of("sahha-api"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("sid", UUID.randomUUID().toString()).claim("cv", 1).claim("roles", List.of())
                .claim("org_id", org.toString()).claim("org_roles", roles).claim("token_type", "access").build());
    }
}
