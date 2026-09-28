package com.sahha.clinical.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Exactly one content field is populated; a child share never serializes its parent record. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SharedClinicalResourceResponse(
        String resourceType, UUID resourceId, UUID patientRegistrationId,
        Instant validUntil, ClinicalRecordResponse consultation,
        ClinicalRecordResponse.DiagnosisItem diagnosis,
        ClinicalRecordResponse.MedicationItem medication,
        ClinicalRecordResponse.HistoryItem allergy) { }
