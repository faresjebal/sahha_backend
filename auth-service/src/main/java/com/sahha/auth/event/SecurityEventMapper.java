package com.sahha.auth.event;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sahha.auth.entity.SecurityEvent;

@Component
public class SecurityEventMapper {

	public AuthSecurityEventMessage toMessage(SecurityEvent event) {
		return new AuthSecurityEventMessage(
				event.getId(),
				event.getUserId(),
				event.getSubjectUserId(),
				event.getNormalizedEmail(),
				event.getEventType().name(),
				event.getResult().name(),
				event.getReasonCode(),
				event.getSessionId(),
				event.getRequestId(),
				event.getIpAddress(),
				event.getUserAgent(),
				event.getOccurredAt());
	}

	public Map<String, Object> toPayload(SecurityEvent event) {
		AuthSecurityEventMessage message = toMessage(event);
		Map<String, Object> payload = new LinkedHashMap<>();
		put(payload, "eventId", message.eventId());
		put(payload, "userId", message.userId());
		put(payload, "subjectUserId", message.subjectUserId());
		put(payload, "normalizedEmail", message.normalizedEmail());
		put(payload, "eventType", message.eventType());
		put(payload, "result", message.result());
		put(payload, "reasonCode", message.reasonCode());
		put(payload, "sessionId", message.sessionId());
		put(payload, "requestId", message.requestId());
		put(payload, "ipAddress", message.ipAddress());
		put(payload, "userAgent", message.userAgent());
		put(payload, "occurredAt", message.occurredAt());
		return Map.copyOf(payload);
	}

	private static void put(
			Map<String, Object> target,
			String key,
			Object value) {
		if (value != null) {
			target.put(key, value);
		}
	}
}
