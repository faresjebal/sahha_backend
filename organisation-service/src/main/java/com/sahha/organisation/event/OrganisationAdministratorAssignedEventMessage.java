package com.sahha.organisation.event;

import java.time.Instant;
import java.util.UUID;

public record OrganisationAdministratorAssignedEventMessage(
		UUID eventId,
		UUID organisationId,
		UUID membershipId,
		UUID userId,
		UUID actorUserId,
		String role,
		String membershipStatus,
		String eventType,
		String requestId,
		Instant occurredAt) {
}
