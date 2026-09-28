package com.sahha.file.client.clinical;

import java.time.Instant;
import java.util.UUID;

public record SharedCareAttachmentContextResource(UUID organisationId, UUID patientRegistrationId,
        UUID patientId, UUID consultationId, UUID actorUserId, String consultationStatus, Instant validUntil) { }
