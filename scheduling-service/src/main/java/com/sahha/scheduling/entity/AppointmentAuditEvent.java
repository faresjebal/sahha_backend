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

@Entity
@Table(name = "appointment_audit_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppointmentAuditEvent {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "appointment_id", nullable = false, updatable = false)
	private UUID appointmentId;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 40,
			updatable = false)
	private AppointmentAuditEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_status", length = 24, updatable = false)
	private AppointmentStatus previousStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_status", nullable = false, length = 24,
			updatable = false)
	private AppointmentStatus newStatus;

	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false, length = 16, updatable = false)
	private AppointmentActorType actorType;

	@Column(name = "actor_membership_id", updatable = false)
	private UUID actorMembershipId;

	@Column(name = "request_id", nullable = false, length = 128,
			updatable = false)
	private String requestId;

	@Column(name = "command_request_id", nullable = false, updatable = false)
	private UUID commandRequestId;

	@Column(name = "transition_reason", length = 500, updatable = false)
	private String transitionReason;

	@Column(name = "previous_starts_at", updatable = false)
	private Instant previousStartsAt;

	@Column(name = "new_starts_at", nullable = false, updatable = false)
	private Instant newStartsAt;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	public static AppointmentAuditEvent booked(
			Appointment appointment,
			UUID actorUserId,
			UUID actorMembershipId,
			String requestId) {
		return booked(
				appointment,
				actorUserId,
				AppointmentActorType.STAFF,
				actorMembershipId,
				requestId);
	}

	public static AppointmentAuditEvent booked(
			Appointment appointment,
			UUID actorUserId,
			AppointmentActorType actorType,
			UUID actorMembershipId,
			String requestId) {
		AppointmentAuditEvent event = new AppointmentAuditEvent();
		event.id = UUID.randomUUID();
		event.appointmentId = appointment.getId();
		event.organisationId = appointment.getOrganisationId();
		event.eventType = AppointmentAuditEventType.APPOINTMENT_BOOKED;
		event.previousStatus = null;
		event.newStatus = appointment.getStatus();
		event.actorUserId = Objects.requireNonNull(actorUserId);
		event.actorType = Objects.requireNonNull(actorType);
		event.actorMembershipId = actorMembershipId;
		validateActor(event.actorType, event.actorMembershipId);
		event.requestId = requiredRequestId(requestId);
		event.commandRequestId = appointment.getBookingRequestId();
		event.transitionReason = null;
		event.previousStartsAt = null;
		event.newStartsAt = appointment.getStartsAt();
		event.occurredAt = appointment.getBookedAt();
		return event;
	}

	public static AppointmentAuditEvent transitioned(
			Appointment appointment,
			AppointmentStatus previousStatus,
			Instant previousStartsAt,
			AppointmentAuditEventType eventType,
			UUID commandRequestId,
			String transitionReason,
			UUID actorUserId,
			UUID actorMembershipId,
			String requestId) {
		AppointmentAuditEvent event = new AppointmentAuditEvent();
		event.id = UUID.randomUUID();
		event.appointmentId = appointment.getId();
		event.organisationId = appointment.getOrganisationId();
		event.eventType = Objects.requireNonNull(eventType);
		event.previousStatus = Objects.requireNonNull(previousStatus);
		event.newStatus = appointment.getStatus();
		event.actorUserId = Objects.requireNonNull(actorUserId);
		event.actorType = AppointmentActorType.STAFF;
		event.actorMembershipId = Objects.requireNonNull(actorMembershipId);
		event.requestId = requiredRequestId(requestId);
		event.commandRequestId = Objects.requireNonNull(commandRequestId);
		event.transitionReason = normalizedReason(transitionReason);
		event.previousStartsAt = Objects.requireNonNull(previousStartsAt);
		event.newStartsAt = appointment.getStartsAt();
		event.occurredAt = appointment.getUpdatedAt();
		return event;
	}

	private static void validateActor(
			AppointmentActorType actorType,
			UUID actorMembershipId) {
		if ((actorType == AppointmentActorType.STAFF)
				!= (actorMembershipId != null)) {
			throw new IllegalArgumentException(
					"actor membership must be present only for staff audit events");
		}
	}

	public boolean matchesCommand(
			UUID appointmentId,
			UUID actorUserId,
			AppointmentAuditEventType eventType,
			String transitionReason,
			Instant newStartsAt) {
		return this.appointmentId.equals(appointmentId)
				&& this.actorUserId.equals(actorUserId)
				&& this.eventType == eventType
				&& Objects.equals(
						this.transitionReason,
						normalizedReason(transitionReason))
				&& (newStartsAt == null
						|| this.newStartsAt.equals(newStartsAt));
	}

	private static String requiredRequestId(String value) {
		Objects.requireNonNull(value, "requestId must not be null");
		String normalized = value.strip();
		if (normalized.isEmpty() || normalized.length() > 128) {
			throw new IllegalArgumentException("requestId is invalid");
		}
		return normalized;
	}

	private static String normalizedReason(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.isEmpty() || normalized.length() > 500) {
			throw new IllegalArgumentException("transition reason is invalid");
		}
		return normalized;
	}
}
