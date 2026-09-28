package com.sahha.clinical.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Discovery metadata only; content is read through the existing author-owned record API. */
public record ReferralSourcePageResponse(List<Source> content, int page, int size,
        long totalElements, int totalPages) {
    public record Source(UUID consultationId, UUID patientRegistrationId,
            UUID appointmentId, Instant finalizedAt, long version) {}
}
