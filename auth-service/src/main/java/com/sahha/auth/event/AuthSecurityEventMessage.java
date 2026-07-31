package com.sahha.auth.event;

import java.time.Instant;
import java.util.UUID;

public record AuthSecurityEventMessage(
		UUID eventId,
		UUID userId,
		UUID subjectUserId,
		String normalizedEmail,
		String eventType,
		String result,
		String reasonCode,
		UUID sessionId,
		String requestId,
		String ipAddress,
		String userAgent,
		Instant occurredAt) {
}
