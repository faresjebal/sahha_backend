package com.sahha.scheduling.entity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "scheduled_appointment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Appointment {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "booking_request_id", nullable = false, updatable = false)
	private UUID bookingRequestId;

	@Column(name = "patient_registration_id", nullable = false, updatable = false)
	private UUID patientRegistrationId;

	@Column(name = "patient_id", nullable = false, updatable = false)
	private UUID patientId;

	@Column(name = "doctor_user_id", nullable = false, updatable = false)
	private UUID doctorUserId;

	@Column(name = "doctor_membership_id", nullable = false, updatable = false)
	private UUID doctorMembershipId;

	@Column(name = "availability_schedule_id", nullable = false)
	private UUID availabilityScheduleId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	@ToString.Include
	private AppointmentStatus status;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Column(name = "time_zone", nullable = false, length = 64)
	private String timeZone;

	@Column(name = "location_label", nullable = false, length = 160,
			updatable = true)
	private String locationLabel;

	@Column(name = "status_reason", length = 500)
	private String statusReason;

	@Column(name = "booked_by_user_id", nullable = false, updatable = false)
	private UUID bookedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "booked_by_actor_type", nullable = false, length = 16,
			updatable = false)
	private AppointmentActorType bookedByActorType;

	@Column(name = "booked_by_membership_id", updatable = false)
	private UUID bookedByMembershipId;

	@Column(name = "booked_at", nullable = false, updatable = false)
	private Instant bookedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static Appointment request(
			UUID organisationId,
			UUID bookingRequestId,
			UUID patientRegistrationId,
			UUID patientId,
			UUID doctorUserId,
			UUID doctorMembershipId,
			UUID availabilityScheduleId,
			Instant startsAt,
			Instant endsAt,
			String timeZone,
			String locationLabel,
			UUID bookedByUserId,
			UUID bookedByMembershipId,
			Clock clock) {
		return request(
				organisationId,
				bookingRequestId,
				patientRegistrationId,
				patientId,
				doctorUserId,
				doctorMembershipId,
				availabilityScheduleId,
				startsAt,
				endsAt,
				timeZone,
				locationLabel,
				bookedByUserId,
				AppointmentActorType.STAFF,
				bookedByMembershipId,
				clock);
	}

	public static Appointment request(
			UUID organisationId,
			UUID bookingRequestId,
			UUID patientRegistrationId,
			UUID patientId,
			UUID doctorUserId,
			UUID doctorMembershipId,
			UUID availabilityScheduleId,
			Instant startsAt,
			Instant endsAt,
			String timeZone,
			String locationLabel,
			UUID bookedByUserId,
			AppointmentActorType bookedByActorType,
			UUID bookedByMembershipId,
			Clock clock) {
		Appointment appointment = new Appointment();
		appointment.id = UUID.randomUUID();
		appointment.organisationId = Objects.requireNonNull(organisationId);
		appointment.bookingRequestId = Objects.requireNonNull(bookingRequestId);
		appointment.patientRegistrationId = Objects.requireNonNull(
				patientRegistrationId);
		appointment.patientId = Objects.requireNonNull(patientId);
		appointment.doctorUserId = Objects.requireNonNull(doctorUserId);
		appointment.doctorMembershipId = Objects.requireNonNull(
				doctorMembershipId);
		appointment.availabilityScheduleId = Objects.requireNonNull(
				availabilityScheduleId);
		appointment.startsAt = Objects.requireNonNull(startsAt);
		appointment.endsAt = Objects.requireNonNull(endsAt);
		if (!appointment.endsAt.isAfter(appointment.startsAt)) {
			throw new IllegalArgumentException("appointment end must follow start");
		}
		appointment.timeZone = validZone(timeZone);
		appointment.locationLabel = DoctorAvailabilitySchedule.required(
				locationLabel, 160, "locationLabel");
		appointment.bookedByUserId = Objects.requireNonNull(bookedByUserId);
		appointment.bookedByActorType = Objects.requireNonNull(bookedByActorType);
		appointment.bookedByMembershipId = bookedByMembershipId;
		if ((bookedByActorType == AppointmentActorType.STAFF)
				!= (bookedByMembershipId != null)) {
			throw new IllegalArgumentException(
					"actor membership must be present only for staff bookings");
		}
		appointment.status = AppointmentStatus.REQUESTED;
		appointment.bookedAt = Instant.now(Objects.requireNonNull(clock));
		appointment.createdAt = appointment.bookedAt;
		appointment.updatedAt = appointment.bookedAt;
		return appointment;
	}

	public boolean matchesInitialRequest(
			UUID actorUserId,
			UUID patientRegistrationId,
			UUID doctorUserId,
			Instant startsAt,
			Instant originallyBookedStart) {
		return bookedByUserId.equals(actorUserId)
				&& this.patientRegistrationId.equals(patientRegistrationId)
				&& this.doctorUserId.equals(doctorUserId)
				&& Objects.equals(originallyBookedStart, startsAt);
	}

	public void confirm(Clock clock) {
		status = AppointmentStatus.CONFIRMED;
		statusReason = null;
		touch(clock);
	}

	public void reject(String reason, Clock clock) {
		status = AppointmentStatus.REJECTED;
		statusReason = requiredReason(reason);
		touch(clock);
	}

	public void reschedule(
			UUID availabilityScheduleId,
			Instant startsAt,
			Instant endsAt,
			String timeZone,
			String locationLabel,
			String reason,
			Clock clock) {
		this.availabilityScheduleId = Objects.requireNonNull(
				availabilityScheduleId);
		this.startsAt = Objects.requireNonNull(startsAt);
		this.endsAt = Objects.requireNonNull(endsAt);
		if (!this.endsAt.isAfter(this.startsAt)) {
			throw new IllegalArgumentException("appointment end must follow start");
		}
		this.timeZone = validZone(timeZone);
		this.locationLabel = DoctorAvailabilitySchedule.required(
				locationLabel, 160, "locationLabel");
		status = AppointmentStatus.RESCHEDULED;
		statusReason = requiredReason(reason);
		touch(clock);
	}

	public void cancel(String reason, Clock clock) {
		status = AppointmentStatus.CANCELLED;
		statusReason = requiredReason(reason);
		touch(clock);
	}

	public void checkIn(Clock clock) {
		status = AppointmentStatus.CHECKED_IN;
		statusReason = null;
		touch(clock);
	}

	public void start(Clock clock) {
		status = AppointmentStatus.IN_PROGRESS;
		statusReason = null;
		touch(clock);
	}

	public void complete(Clock clock) {
		status = AppointmentStatus.COMPLETED;
		statusReason = null;
		touch(clock);
	}

	public void markNoShow(Clock clock) {
		status = AppointmentStatus.NO_SHOW;
		statusReason = null;
		touch(clock);
	}

	private void touch(Clock clock) {
		updatedAt = Instant.now(Objects.requireNonNull(clock));
	}

	private static String requiredReason(String value) {
		return DoctorAvailabilitySchedule.required(value, 500, "reason");
	}

	private static String validZone(String value) {
		String zone = DoctorAvailabilitySchedule.required(
				value, 64, "timeZone");
		ZoneId.of(zone);
		return zone;
	}
}
