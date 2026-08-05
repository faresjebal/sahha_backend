package com.sahha.organisation.event;

import java.time.Instant;
import java.util.UUID;

public record StaffDepartmentAssignmentChangedEventMessage(
		UUID eventId,
		UUID organisationId,
		UUID assignmentId,
		UUID membershipId,
		UUID departmentId,
		UUID userId,
		UUID actorUserId,
		String assignmentStatus,
		String eventType,
		String requestId,
		Instant occurredAt) {
}
