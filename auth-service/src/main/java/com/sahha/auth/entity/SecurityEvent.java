package com.sahha.auth.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "security_event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class SecurityEvent {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "user_id", updatable = false)
	private UUID userId;

	@Column(name = "subject_user_id", updatable = false)
	private UUID subjectUserId;

	@Column(name = "normalized_email", length = 320, updatable = false)
	private String normalizedEmail;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 64, updatable = false)
	@ToString.Include
	private SecurityEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	@ToString.Include
	private SecurityEventResult result;

	@Column(name = "reason_code", length = 64, updatable = false)
	private String reasonCode;

	@Column(name = "session_id", updatable = false)
	private UUID sessionId;

	@Column(name = "request_id", length = 128, updatable = false)
	private String requestId;

	@Column(name = "ip_address", length = 45, updatable = false)
	private String ipAddress;

	@Column(name = "user_agent", length = 512, updatable = false)
	private String userAgent;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	public static SecurityEvent create(
			UUID userId,
			UUID subjectUserId,
			String normalizedEmail,
			SecurityEventType eventType,
			SecurityEventResult result,
			String reasonCode,
			UUID sessionId,
			String requestId,
			String ipAddress,
			String userAgent,
			Instant occurredAt) {
		SecurityEvent event = new SecurityEvent();
		event.id = UUID.randomUUID();
		event.userId = userId;
		event.subjectUserId = subjectUserId;
		event.normalizedEmail = optional(
				normalizedEmail,
				320,
				"normalizedEmail");
		event.eventType = Objects.requireNonNull(
				eventType,
				"eventType must not be null");
		event.result = Objects.requireNonNull(
				result,
				"result must not be null");
		event.reasonCode = optional(reasonCode, 64, "reasonCode");
		event.sessionId = sessionId;
		event.requestId = optional(requestId, 128, "requestId");
		event.ipAddress = optional(ipAddress, 45, "ipAddress");
		event.userAgent = optional(userAgent, 512, "userAgent");
		event.occurredAt = Objects.requireNonNull(
				occurredAt,
				"occurredAt must not be null");
		return event;
	}

	private static String optional(
			String value,
			int maximumLength,
			String fieldName) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String stripped = value.strip();
		if (stripped.length() > maximumLength) {
			throw new IllegalArgumentException(
					fieldName + " exceeds " + maximumLength + " characters");
		}
		return stripped;
	}
}
