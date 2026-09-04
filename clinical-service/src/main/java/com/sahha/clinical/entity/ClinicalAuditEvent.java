package com.sahha.clinical.entity;

import java.time.Instant;
import java.util.Map;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "clinical_audit_event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class ClinicalAuditEvent {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;

	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;

	@Column(name = "appointment_id", nullable = false, updatable = false)
	private UUID appointmentId;

	@Column(name = "patient_id", nullable = false, updatable = false)
	private UUID patientId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 64, updatable = false)
	@ToString.Include
	private ClinicalAuditEventType eventType;

	@Column(nullable = false, length = 16, updatable = false)
	private String result;

	@Column(name = "resource_version", nullable = false, updatable = false)
	private long resourceVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> metadata;

	@Column(name = "request_id", length = 128, updatable = false)
	private String requestId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	public static ClinicalAuditEvent success(
			Consultation consultation,
			UUID actorUserId,
			ClinicalAuditEventType eventType,
			long resourceVersion,
			String requestId,
			Instant occurredAt) {
		Consultation required = Objects.requireNonNull(consultation);
		ClinicalAuditEvent event = new ClinicalAuditEvent();
		event.id = UUID.randomUUID();
		event.organisationId = required.getOrganisationId();
		event.actorUserId = Objects.requireNonNull(actorUserId);
		event.consultationId = required.getId();
		event.appointmentId = required.getAppointmentId();
		event.patientId = required.getPatientId();
		event.eventType = Objects.requireNonNull(eventType);
		event.result = "SUCCESS";
		event.resourceVersion = resourceVersion;
		event.metadata = Map.of("status", required.getStatus().name());
		event.requestId = requestId == null || requestId.isBlank()
				? null : requestId.strip();
		event.occurredAt = Objects.requireNonNull(occurredAt);
		return event;
	}
}
