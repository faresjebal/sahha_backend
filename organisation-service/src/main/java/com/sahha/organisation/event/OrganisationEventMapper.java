package com.sahha.organisation.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sahha.organisation.entity.Organisation;
import com.sahha.organisation.entity.OrganisationAuditEvent;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.Department;
import com.sahha.organisation.entity.DoctorProfile;
import com.sahha.organisation.entity.StaffDepartmentAssignment;
import com.sahha.organisation.entity.StaffInvitation;

@Component
public class OrganisationEventMapper {

	public Map<String, Object> toPayload(
			Organisation organisation,
			OrganisationAuditEvent event) {
		OrganisationCreatedEventMessage message =
				new OrganisationCreatedEventMessage(
						event.getId(),
						organisation.getId(),
						event.getActorUserId(),
						organisation.getType().name(),
						organisation.getStatus().name(),
						event.getEventType().name(),
						event.getRequestId(),
						event.getOccurredAt());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", message.eventId());
		payload.put("organisationId", message.organisationId());
		payload.put("actorUserId", message.actorUserId());
		payload.put("organisationType", message.organisationType());
		payload.put("organisationStatus", message.organisationStatus());
		payload.put("eventType", message.eventType());
		if (message.requestId() != null) {
			payload.put("requestId", message.requestId());
		}
		payload.put("occurredAt", message.occurredAt());
		return Map.copyOf(payload);
	}

	public Map<String, Object> toPayload(
			OrganisationMembership membership,
			OrganisationRole role,
			OrganisationAuditEvent event) {
		OrganisationAdministratorAssignedEventMessage message =
				new OrganisationAdministratorAssignedEventMessage(
						event.getId(),
						membership.getOrganisationId(),
						membership.getId(),
						membership.getUserId(),
						event.getActorUserId(),
						role.name(),
						membership.getStatus().name(),
						event.getEventType().name(),
						event.getRequestId(),
						event.getOccurredAt());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", message.eventId());
		payload.put("organisationId", message.organisationId());
		payload.put("membershipId", message.membershipId());
		payload.put("userId", message.userId());
		payload.put("actorUserId", message.actorUserId());
		payload.put("role", message.role());
		payload.put("membershipStatus", message.membershipStatus());
		payload.put("eventType", message.eventType());
		if (message.requestId() != null) {
			payload.put("requestId", message.requestId());
		}
		payload.put("occurredAt", message.occurredAt());
		return Map.copyOf(payload);
	}

	public Map<String, Object> toPayload(
			Department department,
			OrganisationAuditEvent event) {
		DepartmentChangedEventMessage message =
				new DepartmentChangedEventMessage(
						event.getId(),
						department.getOrganisationId(),
						department.getId(),
						event.getActorUserId(),
						department.getStatus().name(),
						event.getEventType().name(),
						event.getRequestId(),
						event.getOccurredAt());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", message.eventId());
		payload.put("organisationId", message.organisationId());
		payload.put("departmentId", message.departmentId());
		payload.put("actorUserId", message.actorUserId());
		payload.put("departmentStatus", message.departmentStatus());
		payload.put("eventType", message.eventType());
		if (message.requestId() != null) {
			payload.put("requestId", message.requestId());
		}
		payload.put("occurredAt", message.occurredAt());
		return Map.copyOf(payload);
	}

	public Map<String, Object> toPayload(
			StaffInvitation invitation,
			OrganisationAuditEvent event) {
		StaffInvitationChangedEventMessage message =
				new StaffInvitationChangedEventMessage(
						event.getId(),
						invitation.getOrganisationId(),
						invitation.getId(),
						event.getActorUserId(),
						event.getTargetUserId(),
						invitation.getAcceptedMembershipId(),
						invitation.getRole().name(),
						invitation.getStatus().name(),
						event.getEventType().name(),
						event.getRequestId(),
						event.getOccurredAt());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", message.eventId());
		payload.put("organisationId", message.organisationId());
		payload.put("invitationId", message.invitationId());
		payload.put("actorUserId", message.actorUserId());
		if (message.targetUserId() != null) {
			payload.put("targetUserId", message.targetUserId());
		}
		if (message.membershipId() != null) {
			payload.put("membershipId", message.membershipId());
		}
		payload.put("role", message.role());
		payload.put("invitationStatus", message.invitationStatus());
		payload.put("eventType", message.eventType());
		if (message.requestId() != null) {
			payload.put("requestId", message.requestId());
		}
		payload.put("occurredAt", message.occurredAt());
		return Map.copyOf(payload);
	}

	public Map<String, Object> toPayload(
			OrganisationMembership membership,
			OrganisationAuditEvent event) {
		StaffMembershipChangedEventMessage message =
				new StaffMembershipChangedEventMessage(
						event.getId(),
						membership.getOrganisationId(),
						membership.getId(),
						membership.getUserId(),
						event.getActorUserId(),
						membership.getStatus().name(),
						event.getEventType().name(),
						event.getRequestId(),
						event.getOccurredAt());
		Map<String, Object> payload = basePayload(
				message.eventId(),
				message.organisationId(),
				message.actorUserId(),
				message.eventType(),
				message.requestId(),
				message.occurredAt());
		payload.put("membershipId", message.membershipId());
		payload.put("userId", message.userId());
		payload.put("membershipStatus", message.membershipStatus());
		return Map.copyOf(payload);
	}

	public Map<String, Object> toPayload(
			StaffDepartmentAssignment assignment,
			UUID targetUserId,
			OrganisationAuditEvent event) {
		StaffDepartmentAssignmentChangedEventMessage message =
				new StaffDepartmentAssignmentChangedEventMessage(
						event.getId(),
						assignment.getOrganisationId(),
						assignment.getId(),
						assignment.getMembershipId(),
						assignment.getDepartmentId(),
						targetUserId,
						event.getActorUserId(),
						assignment.getStatus().name(),
						event.getEventType().name(),
						event.getRequestId(),
						event.getOccurredAt());
		Map<String, Object> payload = basePayload(
				message.eventId(),
				message.organisationId(),
				message.actorUserId(),
				message.eventType(),
				message.requestId(),
				message.occurredAt());
		payload.put("assignmentId", message.assignmentId());
		payload.put("membershipId", message.membershipId());
		payload.put("departmentId", message.departmentId());
		payload.put("userId", message.userId());
		payload.put("assignmentStatus", message.assignmentStatus());
		return Map.copyOf(payload);
	}

	public Map<String, Object> toPayload(
			DoctorProfile profile,
			UUID targetUserId,
			OrganisationAuditEvent event) {
		DoctorProfileChangedEventMessage message =
				new DoctorProfileChangedEventMessage(
						event.getId(),
						profile.getOrganisationId(),
						profile.getId(),
						profile.getMembershipId(),
						targetUserId,
						event.getActorUserId(),
						event.getEventType().name(),
						event.getRequestId(),
						event.getOccurredAt());
		Map<String, Object> payload = basePayload(
				message.eventId(),
				message.organisationId(),
				message.actorUserId(),
				message.eventType(),
				message.requestId(),
				message.occurredAt());
		payload.put("profileId", message.profileId());
		payload.put("membershipId", message.membershipId());
		payload.put("userId", message.userId());
		return Map.copyOf(payload);
	}

	private static Map<String, Object> basePayload(
			UUID eventId,
			UUID organisationId,
			UUID actorUserId,
			String eventType,
			String requestId,
			java.time.Instant occurredAt) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", eventId);
		payload.put("organisationId", organisationId);
		payload.put("actorUserId", actorUserId);
		payload.put("eventType", eventType);
		if (requestId != null) {
			payload.put("requestId", requestId);
		}
		payload.put("occurredAt", occurredAt);
		return payload;
	}
}
