package com.sahha.scheduling.outbox;

import java.util.Map;
import java.util.UUID;

public record ClaimedAppointmentOutboxEvent(
		UUID eventId,
		UUID claimToken,
		UUID appointmentId,
		int previousPublicationAttempts,
		Map<String, Object> payload) {
}
