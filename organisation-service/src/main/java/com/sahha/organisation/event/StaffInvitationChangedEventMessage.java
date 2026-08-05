package com.sahha.organisation.event;

import java.time.Instant;
import java.util.UUID;

public record StaffInvitationChangedEventMessage(
		UUID eventId,
		UUID organisationId,
		UUID invitationId,
		UUID actorUserId,
		UUID targetUserId,
		UUID membershipId,
		String role,
		String invitationStatus,
		String eventType,
		String requestId,
		Instant occurredAt) {
}
