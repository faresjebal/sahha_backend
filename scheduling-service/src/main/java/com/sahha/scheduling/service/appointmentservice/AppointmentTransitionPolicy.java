package com.sahha.scheduling.service.appointmentservice;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentStatus;
import com.sahha.scheduling.exception.AppointmentNotReadyForNoShowException;
import com.sahha.scheduling.exception.InvalidAppointmentTransitionException;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;

@Component
public class AppointmentTransitionPolicy {

	private static final Map<AppointmentStatus, Set<AppointmentStatus>> MATRIX =
			matrix();

	public void requireActorCanTransition(
			Appointment appointment,
			UUID actorUserId,
			OrganisationContextResource actor,
			AppointmentStatus target) {
		boolean doctorOwner = actor.roles().contains("DOCTOR")
				&& appointment.getDoctorUserId().equals(actorUserId);
		boolean operational = actor.roles().contains("RECEPTIONIST")
				|| actor.roles().contains("ORGANIZATION_ADMIN");
		boolean permitted = switch (target) {
			case CONFIRMED, REJECTED -> doctorOwner;
			case RESCHEDULED, CANCELLED -> doctorOwner || operational;
			case CHECKED_IN -> operational;
			case IN_PROGRESS, COMPLETED -> doctorOwner;
			case NO_SHOW -> doctorOwner || operational;
			default -> false;
		};
		if (!permitted) {
			throw new SchedulingAccessDeniedException();
		}
	}

	public void requireTemporalTransition(
			Appointment appointment,
			AppointmentStatus target,
			Instant now) {
		if (target == AppointmentStatus.NO_SHOW
				&& now.isBefore(appointment.getStartsAt())) {
			throw new AppointmentNotReadyForNoShowException();
		}
	}

	public void requireTransition(
			AppointmentStatus current,
			AppointmentStatus target) {
		if (!MATRIX.getOrDefault(current, Set.of()).contains(target)) {
			throw new InvalidAppointmentTransitionException();
		}
	}

	private static Map<AppointmentStatus, Set<AppointmentStatus>> matrix() {
		Map<AppointmentStatus, Set<AppointmentStatus>> matrix =
				new EnumMap<>(AppointmentStatus.class);
		matrix.put(
				AppointmentStatus.REQUESTED,
				EnumSet.of(
						AppointmentStatus.CONFIRMED,
						AppointmentStatus.REJECTED,
						AppointmentStatus.RESCHEDULED,
						AppointmentStatus.CANCELLED));
		matrix.put(
				AppointmentStatus.RESCHEDULED,
				EnumSet.of(
						AppointmentStatus.CONFIRMED,
						AppointmentStatus.REJECTED,
						AppointmentStatus.RESCHEDULED,
						AppointmentStatus.CANCELLED));
		matrix.put(
				AppointmentStatus.CONFIRMED,
				EnumSet.of(
						AppointmentStatus.RESCHEDULED,
						AppointmentStatus.CANCELLED,
						AppointmentStatus.CHECKED_IN,
						AppointmentStatus.NO_SHOW));
		matrix.put(
				AppointmentStatus.CHECKED_IN,
				EnumSet.of(AppointmentStatus.IN_PROGRESS));
		matrix.put(
				AppointmentStatus.IN_PROGRESS,
				EnumSet.of(AppointmentStatus.COMPLETED));
		return Map.copyOf(matrix);
	}
}
