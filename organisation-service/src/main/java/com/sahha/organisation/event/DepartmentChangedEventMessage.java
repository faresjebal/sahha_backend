package com.sahha.organisation.event;

import java.time.Instant;
import java.util.UUID;

public record DepartmentChangedEventMessage(
		UUID eventId,
		UUID organisationId,
		UUID departmentId,
		UUID actorUserId,
		String departmentStatus,
		String eventType,
		String requestId,
		Instant occurredAt) {
}
