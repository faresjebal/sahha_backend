package com.sahha.communication.entity;

import java.time.Instant;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "communication_outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunicationOutboxEvent {
	private static final String MESSAGE_TOPIC = "sahha.communication.messages.v1";

	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "audit_event_id", nullable = false, updatable = false)
	private UUID auditEventId;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;
	@Column(name = "event_type", nullable = false, length = 64, updatable = false)
	private String eventType;
	@Column(name = "destination_topic", nullable = false, length = 160, updatable = false)
	private String destinationTopic;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb", updatable = false)
	private Map<String,Object> payload;
	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;
	@Column(name = "published_at")
	private Instant publishedAt;
	@Column(name = "publication_attempts", nullable = false)
	private int publicationAttempts;
	@Column(name = "last_attempt_at")
	private Instant lastAttemptAt;
	@Column(name = "last_error_code", length = 128)
	private String lastErrorCode;
	@Version @Column(nullable = false)
	private long version;

	public static CommunicationOutboxEvent pending(CommunicationAuditEvent audit,
			String type, Map<String,Object> payload) {
		return pending(audit, MESSAGE_TOPIC, type, payload);
	}

	public static CommunicationOutboxEvent pending(CommunicationAuditEvent audit,
			String destinationTopic, String type, Map<String,Object> payload) {
		CommunicationOutboxEvent value = new CommunicationOutboxEvent();
		value.id = UUID.randomUUID();
		value.auditEventId = audit.getId();
		value.organisationId = audit.getOrganisationId();
		value.aggregateId = audit.getAggregateId();
		value.eventType = Objects.requireNonNull(type);
		value.destinationTopic = requiredTopic(destinationTopic);
		value.payload = Map.copyOf(payload);
		value.occurredAt = audit.getOccurredAt();
		return value;
	}

	private static String requiredTopic(String value) {
		if (value == null || value.isBlank() || value.length() > 160) {
			throw new IllegalArgumentException("destination topic is invalid");
		}
		return value;
	}

	public void published(Instant now) {
		publicationAttempts = Math.addExact(publicationAttempts, 1);
		lastAttemptAt = Objects.requireNonNull(now);
		publishedAt = now;
		lastErrorCode = null;
	}

	public void failed(Instant now, String error) {
		publicationAttempts = Math.addExact(publicationAttempts, 1);
		lastAttemptAt = Objects.requireNonNull(now);
		lastErrorCode = error == null ? "Unknown" : error.substring(0, Math.min(128, error.length()));
	}
}
