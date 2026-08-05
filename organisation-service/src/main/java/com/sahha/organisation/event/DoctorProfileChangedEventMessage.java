package com.sahha.organisation.event;

import java.time.Instant;
import java.util.UUID;

public record DoctorProfileChangedEventMessage(
		UUID eventId,
		UUID organisationId,
		UUID profileId,
		UUID membershipId,
		UUID userId,
		UUID actorUserId,
		String eventType,
		String requestId,
		Instant occurredAt) {
}
