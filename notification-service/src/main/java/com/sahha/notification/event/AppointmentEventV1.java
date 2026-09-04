package com.sahha.notification.event;

import java.time.Instant;
import java.util.UUID;

public record AppointmentEventV1(
		UUID eventId,
		AppointmentEventType eventType,
		Integer schemaVersion,
		Instant occurredAt,
		UUID appointmentId,
		UUID organisationId,
		UUID patientId,
		UUID doctorUserId,
		UUID actorUserId,
		String requestId,
		AppointmentStatus status,
		Instant startsAt,
		Instant endsAt,
		String timeZone,
		String locationLabel,
		Long resourceVersion,
		AppointmentStatus previousStatus,
		Instant previousStartsAt) {
}
