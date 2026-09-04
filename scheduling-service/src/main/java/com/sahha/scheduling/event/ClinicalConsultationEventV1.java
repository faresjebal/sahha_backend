package com.sahha.scheduling.event;

import java.time.Instant;
import java.util.UUID;

public record ClinicalConsultationEventV1(
		UUID eventId,
		String eventType,
		Integer schemaVersion,
		Instant occurredAt,
		UUID actorUserId,
		UUID organisationId,
		UUID appointmentId,
		UUID consultationId,
		String status,
		Long resourceVersion) {
}
