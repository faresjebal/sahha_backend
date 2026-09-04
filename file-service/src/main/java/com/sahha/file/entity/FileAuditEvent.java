package com.sahha.file.entity;

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

@Entity
@Table(name = "file_audit_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class FileAuditEvent {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;

	@Column(name = "medical_file_id", nullable = false, updatable = false)
	private UUID medicalFileId;

	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;

	@Column(name = "patient_registration_id", nullable = false, updatable = false)
	private UUID patientRegistrationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 48, updatable = false)
	private FileAuditEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	private FileAuditResult result;

	@Column(name = "reason_code", length = 64, updatable = false)
	private String reasonCode;

	@Column(name = "request_id", nullable = false, length = 128, updatable = false)
	private String requestId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	public static FileAuditEvent record(
			MedicalFile file,
			UUID actorUserId,
			FileAuditEventType eventType,
			FileAuditResult result,
			String reasonCode,
			String requestId,
			Instant occurredAt) {
		MedicalFile requiredFile = Objects.requireNonNull(file);
		FileAuditEvent audit = new FileAuditEvent();
		audit.id = UUID.randomUUID();
		audit.organisationId = requiredFile.getOrganisationId();
		audit.actorUserId = Objects.requireNonNull(actorUserId);
		audit.medicalFileId = requiredFile.getId();
		audit.consultationId = requiredFile.getConsultationId();
		audit.patientRegistrationId = requiredFile.getPatientRegistrationId();
		audit.eventType = Objects.requireNonNull(eventType);
		audit.result = Objects.requireNonNull(result);
		audit.reasonCode = optional(reasonCode, 64, "reasonCode");
		audit.requestId = required(requestId, 128, "requestId");
		audit.occurredAt = Objects.requireNonNull(occurredAt);
		return audit;
	}

	private static String optional(String value, int maximumLength, String fieldName) {
		return value == null || value.isBlank() ? null : required(value, maximumLength, fieldName);
	}

	private static String required(String value, int maximumLength, String fieldName) {
		Objects.requireNonNull(value);
		String stripped = value.strip();
		if (stripped.isEmpty() || stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}
}
