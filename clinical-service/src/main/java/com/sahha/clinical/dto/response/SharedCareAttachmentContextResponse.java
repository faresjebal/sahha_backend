package com.sahha.clinical.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Read-only authority; never accepted by the upload/author-write paths. */
public record SharedCareAttachmentContextResponse(UUID organisationId, UUID patientRegistrationId,
        UUID patientId, UUID consultationId, UUID actorUserId, String consultationStatus, Instant validUntil) { }
