package com.sahha.clinical.entity;

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
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "clinical_access_audit_event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalAccessAuditEvent {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;
	@Column(name = "resource_type", nullable = false, length = 32, updatable = false)
	private String resourceType;
	@Column(name = "resource_id", nullable = false, updatable = false)
	private UUID resourceId;
	@Column(name = "patient_registration_id", updatable = false)
	private UUID patientRegistrationId;
	@Column(name = "care_appointment_id", updatable = false)
	private UUID careAppointmentId;
	@Column(nullable = false, length = 16, updatable = false)
	private String result;
	@Column(name = "access_reason", nullable = false, length = 64, updatable = false)
	private String accessReason;
	@Column(name = "request_id", length = 128, updatable = false)
	private String requestId;
	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	public static ClinicalAccessAuditEvent record(
			UUID organisationId,
			UUID actorUserId,
			String resourceType,
			UUID resourceId,
			UUID patientRegistrationId,
			UUID careAppointmentId,
			String result,
			String accessReason,
			String requestId,
			Instant occurredAt) {
		ClinicalAccessAuditEvent event = new ClinicalAccessAuditEvent();
		event.id = UUID.randomUUID();
		event.organisationId = Objects.requireNonNull(organisationId);
		event.actorUserId = Objects.requireNonNull(actorUserId);
		event.resourceType = required(resourceType, 32);
		event.resourceId = Objects.requireNonNull(resourceId);
		event.patientRegistrationId = patientRegistrationId;
		event.careAppointmentId = careAppointmentId;
		event.result = required(result, 16);
		event.accessReason = required(accessReason, 64);
		event.requestId = optional(requestId, 128);
		event.occurredAt = Objects.requireNonNull(occurredAt);
		return event;
	}

	private static String required(String value, int maximumLength) {
		String normalized = Objects.requireNonNull(value).strip();
		if (normalized.isEmpty() || normalized.length() > maximumLength) {
			throw new IllegalArgumentException("audit value is invalid");
		}
		return normalized;
	}

	private static String optional(String value, int maximumLength) {
		return value == null || value.isBlank()
				? null : required(value, maximumLength);
	}
}
