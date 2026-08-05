package com.sahha.patient.entity;

import java.time.Instant;
import java.util.LinkedHashMap;
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
@Table(name = "patient_audit_event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class PatientAuditEvent {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;

	@Column(name = "resource_type", nullable = false, length = 40,
			updatable = false)
	private String resourceType;

	@Column(name = "resource_id", nullable = false, updatable = false)
	private UUID resourceId;

	@Column(name = "patient_id", updatable = false)
	private UUID patientId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 64,
			updatable = false)
	@ToString.Include
	private PatientAuditEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	private PatientAuditResult result;

	@Column(name = "resource_version", updatable = false)
	private Long resourceVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> metadata;

	@Column(name = "request_id", length = 128, updatable = false)
	private String requestId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	public static PatientAuditEvent directoryActivity(
			UUID organisationId,
			UUID actorUserId,
			PatientAuditEventType eventType,
			Map<String, Object> metadata,
			String requestId,
			Instant occurredAt) {
		if (eventType != PatientAuditEventType.PATIENT_DUPLICATE_CHECKED
				&& eventType
						!= PatientAuditEventType.PATIENT_DIRECTORY_SEARCHED) {
			throw new IllegalArgumentException("eventType is not directory activity");
		}
		return create(
				organisationId,
				actorUserId,
				"PATIENT_DIRECTORY",
				organisationId,
				null,
				eventType,
				null,
				metadata,
				requestId,
				occurredAt);
	}

	public static PatientAuditEvent registrationActivity(
			PatientOrganisationRegistration registration,
			UUID actorUserId,
			PatientAuditEventType eventType,
			Map<String, Object> metadata,
			String requestId,
			Instant occurredAt) {
		PatientOrganisationRegistration requiredRegistration =
				Objects.requireNonNull(
						registration,
						"registration must not be null");
		if (eventType == PatientAuditEventType.PATIENT_DUPLICATE_CHECKED
				|| eventType
						== PatientAuditEventType.PATIENT_DIRECTORY_SEARCHED) {
			throw new IllegalArgumentException(
					"eventType is not registration activity");
		}
		return create(
				requiredRegistration.getOrganisationId(),
				actorUserId,
				"PATIENT_REGISTRATION",
				requiredRegistration.getId(),
				requiredRegistration.getPatient().getId(),
				eventType,
				requiredRegistration.getVersion(),
				metadata,
				requestId,
				occurredAt);
	}

	private static PatientAuditEvent create(
			UUID organisationId,
			UUID actorUserId,
			String resourceType,
			UUID resourceId,
			UUID patientId,
			PatientAuditEventType eventType,
			Long resourceVersion,
			Map<String, Object> metadata,
			String requestId,
			Instant occurredAt) {
		PatientAuditEvent event = new PatientAuditEvent();
		event.id = UUID.randomUUID();
		event.organisationId = Objects.requireNonNull(
				organisationId,
				"organisationId must not be null");
		event.actorUserId = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		event.resourceType = resourceType;
		event.resourceId = Objects.requireNonNull(
				resourceId,
				"resourceId must not be null");
		event.patientId = patientId;
		event.eventType = Objects.requireNonNull(
				eventType,
				"eventType must not be null");
		event.result = PatientAuditResult.SUCCESS;
		event.resourceVersion = resourceVersion;
		event.metadata = Map.copyOf(new LinkedHashMap<>(
				metadata == null ? Map.of() : metadata));
		event.requestId = optional(requestId, 128, "requestId");
		event.occurredAt = Objects.requireNonNull(
				occurredAt,
				"occurredAt must not be null");
		return event;
	}

	private static String optional(
			String value,
			int maximumLength,
			String fieldName) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String stripped = value.strip();
		if (stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}
}
