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

	@Column(name = "resource_type", nullable = false, length = 40, updatable = false)
	private String resourceType;

	@Column(name = "resource_id", nullable = false, updatable = false)
	private UUID resourceId;

	@Column(name = "target_user_id", updatable = false)
	private UUID targetUserId;

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
		event.resourceType = "ORGANISATION";
		event.resourceId = requiredOrganisation.getId();
		event.eventType = OrganisationAuditEventType.ORGANISATION_CREATED;
		event.result = OrganisationAuditResult.SUCCESS;
		event.requestId = optional(requestId, 128, "requestId");
		event.occurredAt = Objects.requireNonNull(
				occurredAt,
				"occurredAt must not be null");
		return event;
	}

	public static OrganisationAuditEvent profileUpdated(Organisation organisation,
			UUID actorUserId, String requestId, Instant occurredAt) {
		OrganisationAuditEvent event = created(organisation, actorUserId, requestId, occurredAt);
		event.eventType = OrganisationAuditEventType.ORGANISATION_PROFILE_UPDATED;
		return event;
	}

	public static OrganisationAuditEvent administratorAssigned(
			OrganisationMembership membership,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		OrganisationMembership requiredMembership = Objects.requireNonNull(
				membership,
				"membership must not be null");
		OrganisationAuditEvent event = new OrganisationAuditEvent();
		event.id = UUID.randomUUID();
		event.organisationId = requiredMembership.getOrganisationId();
		event.actorUserId = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		event.resourceType = "MEMBERSHIP";
		event.resourceId = requiredMembership.getId();
		event.targetUserId = requiredMembership.getUserId();
		event.eventType =
				OrganisationAuditEventType.ORGANISATION_ADMIN_ASSIGNED;
		event.result = OrganisationAuditResult.SUCCESS;
		event.requestId = optional(requestId, 128, "requestId");
		event.occurredAt = Objects.requireNonNull(
				occurredAt,
				"occurredAt must not be null");
		return event;
	}

	public static OrganisationAuditEvent departmentChanged(
			Department department,
			OrganisationAuditEventType eventType,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		Department requiredDepartment = Objects.requireNonNull(
				department,
				"department must not be null");
		OrganisationAuditEventType requiredType = Objects.requireNonNull(
				eventType,
				"eventType must not be null");
		if (requiredType != OrganisationAuditEventType.DEPARTMENT_CREATED
				&& requiredType != OrganisationAuditEventType.DEPARTMENT_UPDATED
				&& requiredType
						!= OrganisationAuditEventType.DEPARTMENT_STATUS_CHANGED) {
			throw new IllegalArgumentException(
					"eventType is not a department event");
		}
		OrganisationAuditEvent event = new OrganisationAuditEvent();
		event.id = UUID.randomUUID();
		event.organisationId = requiredDepartment.getOrganisationId();
		event.actorUserId = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		event.resourceType = "DEPARTMENT";
		event.resourceId = requiredDepartment.getId();
		event.eventType = requiredType;
		event.result = OrganisationAuditResult.SUCCESS;
		event.requestId = optional(requestId, 128, "requestId");
		event.occurredAt = Objects.requireNonNull(
				occurredAt,
				"occurredAt must not be null");
		return event;
	}

	public static OrganisationAuditEvent staffInvitationChanged(
			StaffInvitation invitation,
			OrganisationAuditEventType eventType,
			UUID actorUserId,
			UUID targetUserId,
			String requestId,
			Instant occurredAt) {
		StaffInvitation requiredInvitation = Objects.requireNonNull(
				invitation,
				"invitation must not be null");
		OrganisationAuditEventType requiredType = Objects.requireNonNull(
				eventType,
				"eventType must not be null");
		if (requiredType != OrganisationAuditEventType.STAFF_INVITATION_CREATED
				&& requiredType
						!= OrganisationAuditEventType.STAFF_INVITATION_RENEWED
				&& requiredType
						!= OrganisationAuditEventType.STAFF_INVITATION_REVOKED
				&& requiredType
						!= OrganisationAuditEventType.STAFF_INVITATION_ACCEPTED
				&& requiredType
						!= OrganisationAuditEventType.STAFF_INVITATION_REJECTED) {
			throw new IllegalArgumentException(
					"eventType is not a staff invitation event");
		}
		OrganisationAuditEvent event = new OrganisationAuditEvent();
		event.id = UUID.randomUUID();
		event.organisationId = requiredInvitation.getOrganisationId();
		event.actorUserId = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		event.resourceType = "STAFF_INVITATION";
		event.resourceId = requiredInvitation.getId();
		event.targetUserId = targetUserId;
		event.eventType = requiredType;
		event.result = OrganisationAuditResult.SUCCESS;
		event.requestId = optional(requestId, 128, "requestId");
		event.occurredAt = Objects.requireNonNull(
				occurredAt,
				"occurredAt must not be null");
		return event;
	}

	public static OrganisationAuditEvent staffMembershipChanged(
			OrganisationMembership membership,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		OrganisationMembership requiredMembership = Objects.requireNonNull(
				membership,
				"membership must not be null");
		return staffResourceChanged(
				requiredMembership.getOrganisationId(),
				"MEMBERSHIP",
				requiredMembership.getId(),
				requiredMembership.getUserId(),
				OrganisationAuditEventType.STAFF_MEMBERSHIP_STATUS_CHANGED,
				actorUserId,
				requestId,
				occurredAt);
	}

	public static OrganisationAuditEvent staffDepartmentAssignmentChanged(
			StaffDepartmentAssignment assignment,
			UUID targetUserId,
			OrganisationAuditEventType eventType,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		StaffDepartmentAssignment requiredAssignment = Objects.requireNonNull(
				assignment,
				"assignment must not be null");
		OrganisationAuditEventType requiredType = Objects.requireNonNull(
				eventType,
				"eventType must not be null");
		if (requiredType != OrganisationAuditEventType.STAFF_DEPARTMENT_ASSIGNED
				&& requiredType
						!= OrganisationAuditEventType.STAFF_DEPARTMENT_ASSIGNMENT_ENDED) {
			throw new IllegalArgumentException(
					"eventType is not a staff department assignment event");
		}
		return staffResourceChanged(
				requiredAssignment.getOrganisationId(),
				"DEPARTMENT_ASSIGNMENT",
				requiredAssignment.getId(),
				targetUserId,
				requiredType,
				actorUserId,
				requestId,
				occurredAt);
	}

	public static OrganisationAuditEvent doctorProfileChanged(
			DoctorProfile profile,
			UUID targetUserId,
			OrganisationAuditEventType eventType,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		DoctorProfile requiredProfile = Objects.requireNonNull(
				profile,
				"profile must not be null");
		OrganisationAuditEventType requiredType = Objects.requireNonNull(
				eventType,
				"eventType must not be null");
		if (requiredType != OrganisationAuditEventType.DOCTOR_PROFILE_CREATED
				&& requiredType
						!= OrganisationAuditEventType.DOCTOR_PROFILE_UPDATED) {
			throw new IllegalArgumentException(
					"eventType is not a doctor profile event");
		}
		return staffResourceChanged(
				requiredProfile.getOrganisationId(),
				"DOCTOR_PROFILE",
				requiredProfile.getId(),
				targetUserId,
				requiredType,
				actorUserId,
				requestId,
				occurredAt);
	}

	private static OrganisationAuditEvent staffResourceChanged(
			UUID organisationId,
			String resourceType,
			UUID resourceId,
			UUID targetUserId,
			OrganisationAuditEventType eventType,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		OrganisationAuditEvent event = new OrganisationAuditEvent();
		event.id = UUID.randomUUID();
		event.organisationId = Objects.requireNonNull(
				organisationId,
				"organisationId must not be null");
		event.actorUserId = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		event.resourceType = Objects.requireNonNull(
				resourceType,
				"resourceType must not be null");
		event.resourceId = Objects.requireNonNull(
				resourceId,
				"resourceId must not be null");
		event.targetUserId = Objects.requireNonNull(
				targetUserId,
				"targetUserId must not be null");
		event.eventType = Objects.requireNonNull(
				eventType,
				"eventType must not be null");
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
