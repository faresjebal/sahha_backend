package com.sahha.notification.entity;

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

import com.sahha.notification.event.CommunicationEventSource;
import com.sahha.notification.event.CommunicationEventV1;

@Entity
@Table(name = "consumed_communication_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumedCommunicationEvent {
	@Id @Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;
	@Column(name = "source_topic", nullable = false, length = 249, updatable = false)
	private String sourceTopic;
	@Column(name = "source_partition", nullable = false, updatable = false)
	private int sourcePartition;
	@Column(name = "source_offset", nullable = false, updatable = false)
	private long sourceOffset;
	@Column(name = "event_type", nullable = false, length = 48, updatable = false)
	private String eventType;
	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;
	@Column(name = "message_id", nullable = false, updatable = false)
	private UUID messageId;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "sender_user_id", nullable = false, updatable = false)
	private UUID senderUserId;
	@Column(name = "resource_version", nullable = false, updatable = false)
	private long resourceVersion;
	@Column(name = "event_occurred_at", nullable = false, updatable = false)
	private Instant eventOccurredAt;
	@Column(name = "recipient_count", nullable = false, updatable = false)
	private int recipientCount;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ConsumedEventOutcome outcome;
	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	public static ConsumedCommunicationEvent processing(CommunicationEventV1 event,
			CommunicationEventSource source, Instant processedAt) {
		ConsumedCommunicationEvent value = new ConsumedCommunicationEvent();
		value.eventId = Objects.requireNonNull(event.eventId());
		value.sourceTopic = Objects.requireNonNull(source.topic());
		value.sourcePartition = source.partition();
		value.sourceOffset = source.offset();
		value.eventType = "MESSAGE_SENT";
		value.conversationId = Objects.requireNonNull(event.conversationId());
		value.messageId = Objects.requireNonNull(event.messageId());
		value.organisationId = Objects.requireNonNull(event.organisationId());
		value.senderUserId = Objects.requireNonNull(event.senderUserId());
		value.resourceVersion = Objects.requireNonNull(event.resourceVersion());
		value.eventOccurredAt = Objects.requireNonNull(event.occurredAt());
		value.recipientCount = event.recipientUserIds().size();
		value.outcome = ConsumedEventOutcome.PROCESSING;
		value.processedAt = Objects.requireNonNull(processedAt);
		return value;
	}

	public void complete(Instant processedAt) {
		if (outcome != ConsumedEventOutcome.PROCESSING) {
			throw new IllegalStateException("consumed communication event is complete");
		}
		outcome = ConsumedEventOutcome.NOTIFICATION_CREATED;
		this.processedAt = Objects.requireNonNull(processedAt);
	}
}
