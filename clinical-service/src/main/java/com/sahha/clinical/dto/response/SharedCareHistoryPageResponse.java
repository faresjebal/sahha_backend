package com.sahha.clinical.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Discovery metadata only. Each record read requires a fresh live care decision. */
public record SharedCareHistoryPageResponse(UUID organisationId, UUID patientRegistrationId,
        Instant validUntil, List<Encounter> content, int page, int size, long totalElements, int totalPages) {
    public record Encounter(UUID consultationId, UUID appointmentId, UUID doctorUserId,
            Instant finalizedAt, long version) { }
}
