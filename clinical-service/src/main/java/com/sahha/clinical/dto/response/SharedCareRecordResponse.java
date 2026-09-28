package com.sahha.clinical.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SharedCareRecordResponse(UUID organisationId, UUID patientRegistrationId,
        Instant validUntil, ClinicalRecordResponse consultation) { }
