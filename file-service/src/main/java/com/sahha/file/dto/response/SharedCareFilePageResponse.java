package com.sahha.file.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SharedCareFilePageResponse(UUID organisationId, UUID patientRegistrationId, UUID consultationId,
        Instant validUntil, List<MedicalFileResource> content, int page, int size, long totalElements, int totalPages) { }
