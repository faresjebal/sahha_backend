package com.sahha.communication.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "communication_audit_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunicationAuditEvent {
	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;
	@Column(name = "message_id", updatable = false)
	private UUID messageId;
	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;
	@Column(name = "event_type", nullable = false, length = 48, updatable = false)
	private String eventType;
	@Column(name = "resource_version", nullable = false, updatable = false)
	private long resourceVersion;
	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	public static CommunicationAuditEvent record(UUID organisationId,
			UUID conversationId, UUID messageId, UUID actorUserId,
			String eventType, long version, Instant now) {
		CommunicationAuditEvent value = new CommunicationAuditEvent();
		value.id = UUID.randomUUID();
		value.organisationId = Objects.requireNonNull(organisationId);
		value.conversationId = Objects.requireNonNull(conversationId);
		value.messageId = messageId;
		value.actorUserId = Objects.requireNonNull(actorUserId);
		value.eventType = Objects.requireNonNull(eventType);
		value.resourceVersion = version;
		value.occurredAt = Objects.requireNonNull(now);
		return value;
	}
}
