package com.sahha.organisation.entity;

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
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "organisation_audit_event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class OrganisationAuditEvent {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 64, updatable = false)
	@ToString.Include
	private OrganisationAuditEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	@ToString.Include
	private OrganisationAuditResult result;

	@Column(name = "request_id", length = 128, updatable = false)
	private String requestId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	public static OrganisationAuditEvent created(
			Organisation organisation,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		Organisation requiredOrganisation = Objects.requireNonNull(
				organisation,
				"organisation must not be null");
		OrganisationAuditEvent event = new OrganisationAuditEvent();
		event.id = UUID.randomUUID();
		event.organisationId = requiredOrganisation.getId();
		event.actorUserId = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		event.eventType = OrganisationAuditEventType.ORGANISATION_CREATED;
		event.result = OrganisationAuditResult.SUCCESS;
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
