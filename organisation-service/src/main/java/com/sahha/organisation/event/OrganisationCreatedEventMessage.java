package com.sahha.organisation.event;

import java.time.Instant;
import java.util.UUID;

public record OrganisationCreatedEventMessage(
		UUID eventId,
		UUID organisationId,
		UUID actorUserId,
		String organisationType,
		String organisationStatus,
		String eventType,
		String requestId,
		Instant occurredAt) {
}
