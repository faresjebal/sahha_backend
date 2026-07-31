package com.sahha.organisation.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organisation_outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class OrganisationOutboxEvent {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "audit_event_id", nullable = false, updatable = false)
	private UUID auditEventId;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "event_type", nullable = false, length = 64, updatable = false)
	@ToString.Include
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> payload;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "publication_attempts", nullable = false)
	private int publicationAttempts;

	@Column(name = "last_attempt_at")
	private Instant lastAttemptAt;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "last_error_code", length = 128)
	private String lastErrorCode;

	@Version
	@Column(nullable = false)
	private long version;

	public static OrganisationOutboxEvent pending(
			OrganisationAuditEvent auditEvent,
			Map<String, Object> payload) {
		OrganisationAuditEvent requiredEvent = Objects.requireNonNull(
				auditEvent,
				"auditEvent must not be null");
		OrganisationOutboxEvent outbox = new OrganisationOutboxEvent();
		outbox.id = UUID.randomUUID();
		outbox.auditEventId = requiredEvent.getId();
		outbox.organisationId = requiredEvent.getOrganisationId();
		outbox.eventType = requiredEvent.getEventType().name();
		outbox.payload = Map.copyOf(new LinkedHashMap<>(
				Objects.requireNonNull(payload, "payload must not be null")));
		outbox.occurredAt = requiredEvent.getOccurredAt();
		outbox.nextAttemptAt = requiredEvent.getOccurredAt();
		return outbox;
	}

	public void markPublished(Instant publishedAt) {
		Instant requiredPublishedAt = requireAttemptTime(publishedAt);
		publicationAttempts = Math.addExact(publicationAttempts, 1);
		lastAttemptAt = requiredPublishedAt;
		this.publishedAt = requiredPublishedAt;
		lastErrorCode = null;
	}

	public void markFailed(
			Instant attemptedAt,
			Duration retryDelay,
			String errorCode) {
		Instant requiredAttemptedAt = requireAttemptTime(attemptedAt);
		Duration requiredRetryDelay = Objects.requireNonNull(
				retryDelay,
				"retryDelay must not be null");
		if (requiredRetryDelay.isNegative() || requiredRetryDelay.isZero()) {
			throw new IllegalArgumentException("retryDelay must be positive");
		}
		publicationAttempts = Math.addExact(publicationAttempts, 1);
		lastAttemptAt = requiredAttemptedAt;
		nextAttemptAt = requiredAttemptedAt.plus(requiredRetryDelay);
		lastErrorCode = required(errorCode, 128, "errorCode");
	}

	private Instant requireAttemptTime(Instant value) {
		Instant required = Objects.requireNonNull(
				value,
				"attempt time must not be null");
		if (required.isBefore(occurredAt)) {
			throw new IllegalArgumentException(
					"attempt time must not precede occurrence");
		}
		return required;
	}

	private static String required(
			String value,
			int maximumLength,
			String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		String stripped = value.strip();
		if (stripped.isEmpty() || stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}
}
