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
import lombok.ToString;

import com.sahha.notification.event.AppointmentEventSource;
import com.sahha.notification.event.AppointmentEventType;
import com.sahha.notification.event.AppointmentEventV1;

@Entity
@Table(name = "consumed_appointment_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class ConsumedAppointmentEvent {

	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	@ToString.Include
	private UUID eventId;

	@Column(name = "source_topic", nullable = false, length = 249,
			updatable = false)
	private String sourceTopic;

	@Column(name = "source_partition", nullable = false, updatable = false)
	private int sourcePartition;

	@Column(name = "source_offset", nullable = false, updatable = false)
	private long sourceOffset;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 48,
			updatable = false)
	@ToString.Include
	private AppointmentEventType eventType;

	@Column(name = "appointment_id", nullable = false, updatable = false)
	private UUID appointmentId;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "resource_version", nullable = false, updatable = false)
	private long resourceVersion;

	@Column(name = "event_occurred_at", nullable = false, updatable = false)
	private Instant eventOccurredAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	@ToString.Include
	private ConsumedEventOutcome outcome;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	public static ConsumedAppointmentEvent processing(
			AppointmentEventV1 event,
			AppointmentEventSource source,
			Instant processedAt) {
		ConsumedAppointmentEvent consumed = new ConsumedAppointmentEvent();
		consumed.eventId = Objects.requireNonNull(event.eventId());
		consumed.sourceTopic = Objects.requireNonNull(source.topic());
		consumed.sourcePartition = source.partition();
		consumed.sourceOffset = source.offset();
		consumed.eventType = Objects.requireNonNull(event.eventType());
		consumed.appointmentId = Objects.requireNonNull(event.appointmentId());
		consumed.organisationId = Objects.requireNonNull(event.organisationId());
		consumed.resourceVersion = Objects.requireNonNull(
				event.resourceVersion());
		consumed.eventOccurredAt = Objects.requireNonNull(event.occurredAt());
		consumed.outcome = ConsumedEventOutcome.PROCESSING;
		consumed.processedAt = Objects.requireNonNull(processedAt);
		return consumed;
	}

	public void complete(
			ConsumedEventOutcome outcome,
			Instant processedAt) {
		if (this.outcome != ConsumedEventOutcome.PROCESSING) {
			throw new IllegalStateException("consumed event is already complete");
		}
		if (outcome == null || outcome == ConsumedEventOutcome.PROCESSING) {
			throw new IllegalArgumentException("final outcome is invalid");
		}
		this.outcome = outcome;
		this.processedAt = Objects.requireNonNull(processedAt);
	}
}
