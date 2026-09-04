package com.sahha.file.outbox;

import java.util.Map;
import java.util.UUID;

public record ClaimedFileOutboxEvent(
		UUID eventId,
		UUID claimToken,
		UUID medicalFileId,
		int previousPublicationAttempts,
		Map<String, Object> payload) {
}
