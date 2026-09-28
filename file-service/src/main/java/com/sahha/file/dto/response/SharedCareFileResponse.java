package com.sahha.file.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SharedCareFileResponse(UUID organisationId, UUID patientRegistrationId,
        Instant validUntil, MedicalFileResource file) { }
