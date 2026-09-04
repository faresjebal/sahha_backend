package com.sahha.scheduling.entity;

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

import com.sahha.scheduling.event.ClinicalConsultationEventV1;
import com.sahha.scheduling.event.ClinicalEventSource;

@Entity
@Table(name = "consumed_clinical_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumedClinicalEvent {

	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;
	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;
	@Column(name = "appointment_id", nullable = false, updatable = false)
	private UUID appointmentId;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;
	@Column(name = "event_type", nullable = false, length = 64, updatable = false)
	private String eventType;
	@Column(name = "clinical_resource_version", nullable = false, updatable = false)
	private long clinicalResourceVersion;
	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;
	@Column(name = "processed_at", nullable = false, updatable = false)
	private Instant processedAt;
	@Column(name = "source_type", nullable = false, length = 16, updatable = false)
	private String sourceType;
	@Column(name = "source_topic", length = 255, updatable = false)
	private String sourceTopic;
	@Column(name = "source_partition", updatable = false)
	private Integer sourcePartition;
	@Column(name = "source_offset", updatable = false)
	private Long sourceOffset;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24, updatable = false)
	private ClinicalCompletionOutcome outcome;
	@Enumerated(EnumType.STRING)
	@Column(name = "previous_status", length = 24, updatable = false)
	private AppointmentStatus previousStatus;
	@Enumerated(EnumType.STRING)
	@Column(name = "resulting_status", length = 24, updatable = false)
	private AppointmentStatus resultingStatus;
	@Column(name = "appointment_version", updatable = false)
	private Long appointmentVersion;
	@Column(name = "conflict_code", length = 64, updatable = false)
	private String conflictCode;

	public static ConsumedClinicalEvent record(
			ClinicalConsultationEventV1 event,
			ClinicalEventSource source,
			ClinicalCompletionOutcome outcome,
			AppointmentStatus previousStatus,
			AppointmentStatus resultingStatus,
			Long appointmentVersion,
			String conflictCode,
			Instant processedAt) {
		ConsumedClinicalEvent consumed = new ConsumedClinicalEvent();
		consumed.eventId = event.eventId();
		consumed.consultationId = event.consultationId();
		consumed.appointmentId = event.appointmentId();
		consumed.organisationId = event.organisationId();
		consumed.actorUserId = event.actorUserId();
		consumed.eventType = event.eventType();
		consumed.clinicalResourceVersion = event.resourceVersion();
		consumed.occurredAt = event.occurredAt();
		consumed.processedAt = Objects.requireNonNull(processedAt);
		consumed.sourceType = source.type();
		consumed.sourceTopic = source.topic();
		consumed.sourcePartition = source.partition();
		consumed.sourceOffset = source.offset();
		consumed.outcome = Objects.requireNonNull(outcome);
		consumed.previousStatus = previousStatus;
		consumed.resultingStatus = resultingStatus;
		consumed.appointmentVersion = appointmentVersion;
		consumed.conflictCode = conflictCode;
		return consumed;
	}
}
