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
	@Column(name = "aggregate_type", nullable = false, length = 32, updatable = false)
	private String aggregateType;
	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;
	@Column(name = "target_resource_type", length = 32, updatable = false)
	private String targetResourceType;
	@Column(name = "target_resource_id", updatable = false)
	private UUID targetResourceId;
	@Column(name = "conversation_id", updatable = false)
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
		value.aggregateType = "CONVERSATION";
		value.aggregateId = Objects.requireNonNull(conversationId);
		value.targetResourceType = messageId == null ? null : "MESSAGE";
		value.targetResourceId = messageId;
		value.conversationId = Objects.requireNonNull(conversationId);
		value.messageId = messageId;
		value.actorUserId = Objects.requireNonNull(actorUserId);
		value.eventType = Objects.requireNonNull(eventType);
		value.resourceVersion = version;
		value.occurredAt = Objects.requireNonNull(now);
		return value;
	}

	public static CommunicationAuditEvent recordReferral(UUID organisationId,
			UUID referralId, UUID actorUserId, String eventType,
			long version, Instant now) {
		return recordResource(organisationId, "REFERRAL", referralId,
				null, null, actorUserId, eventType, version, now);
	}

	public static CommunicationAuditEvent recordAccessDecision(UUID organisationId,
			UUID aggregateId, String aggregateType, UUID targetResourceId,
			String targetResourceType, UUID actorUserId, String eventType, Instant now) {
		return recordResource(organisationId, aggregateType, aggregateId,
				targetResourceType, targetResourceId, actorUserId, eventType, 0, now);
	}

	public static CommunicationAuditEvent recordResourceChange(UUID organisationId,
			String aggregateType, UUID aggregateId, String targetResourceType,
			UUID targetResourceId, UUID actorUserId, String eventType,
			long version, Instant now) {
		return recordResource(organisationId, aggregateType, aggregateId,
				targetResourceType, targetResourceId, actorUserId, eventType, version, now);
	}

	private static CommunicationAuditEvent recordResource(UUID organisationId,
			String aggregateType, UUID aggregateId, String targetResourceType,
			UUID targetResourceId, UUID actorUserId, String eventType,
			long version, Instant now) {
		CommunicationAuditEvent value = new CommunicationAuditEvent();
		value.id = UUID.randomUUID();
		value.organisationId = Objects.requireNonNull(organisationId);
		value.aggregateType = Objects.requireNonNull(aggregateType);
		value.aggregateId = Objects.requireNonNull(aggregateId);
		value.targetResourceType = targetResourceType;
		value.targetResourceId = targetResourceId;
		value.actorUserId = Objects.requireNonNull(actorUserId);
		value.eventType = Objects.requireNonNull(eventType);
		value.resourceVersion = version;
		value.occurredAt = Objects.requireNonNull(now);
		return value;
	}
}
