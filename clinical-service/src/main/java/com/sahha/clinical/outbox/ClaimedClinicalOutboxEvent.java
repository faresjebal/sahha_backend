package com.sahha.clinical.outbox;

import java.util.Map;
import java.util.UUID;

public record ClaimedClinicalOutboxEvent(
		UUID eventId,
		UUID claimToken,
		UUID consultationId,
		int previousPublicationAttempts,
		Map<String, Object> payload) {
}
