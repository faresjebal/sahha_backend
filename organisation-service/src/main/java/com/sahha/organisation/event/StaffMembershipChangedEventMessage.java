package com.sahha.organisation.event;

import java.time.Instant;
import java.util.UUID;

public record StaffMembershipChangedEventMessage(
		UUID eventId,
		UUID organisationId,
		UUID membershipId,
		UUID userId,
		UUID actorUserId,
		String membershipStatus,
		String eventType,
		String requestId,
		Instant occurredAt) {
}
