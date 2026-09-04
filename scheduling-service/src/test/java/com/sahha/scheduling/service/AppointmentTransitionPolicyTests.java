package com.sahha.scheduling.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentStatus;
import com.sahha.scheduling.exception.AppointmentNotReadyForNoShowException;
import com.sahha.scheduling.exception.InvalidAppointmentTransitionException;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;
import com.sahha.scheduling.service.appointmentservice.AppointmentTransitionPolicy;

class AppointmentTransitionPolicyTests {

	private final AppointmentTransitionPolicy policy =
			new AppointmentTransitionPolicy();

	@Test
	void explicitMatrixKeepsTerminalStatesClosed() {
		assertDoesNotThrow(() -> policy.requireTransition(
				AppointmentStatus.REQUESTED, AppointmentStatus.CONFIRMED));
		assertDoesNotThrow(() -> policy.requireTransition(
				AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED));
		assertDoesNotThrow(() -> policy.requireTransition(
				AppointmentStatus.RESCHEDULED, AppointmentStatus.REJECTED));
		assertDoesNotThrow(() -> policy.requireTransition(
				AppointmentStatus.CONFIRMED, AppointmentStatus.CHECKED_IN));
		assertDoesNotThrow(() -> policy.requireTransition(
				AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS));
		assertDoesNotThrow(() -> policy.requireTransition(
				AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED));
		assertDoesNotThrow(() -> policy.requireTransition(
				AppointmentStatus.CONFIRMED, AppointmentStatus.NO_SHOW));
		assertThrows(InvalidAppointmentTransitionException.class, () ->
				policy.requireTransition(
						AppointmentStatus.CONFIRMED,
						AppointmentStatus.REJECTED));
		assertThrows(InvalidAppointmentTransitionException.class, () ->
				policy.requireTransition(
						AppointmentStatus.CANCELLED,
						AppointmentStatus.CONFIRMED));
		assertThrows(InvalidAppointmentTransitionException.class, () ->
				policy.requireTransition(
						AppointmentStatus.COMPLETED,
						AppointmentStatus.CHECKED_IN));
	}

	@Test
	void onlyOwningDoctorCanConfirmWhileOperationsCanReschedule() {
		UUID organisationId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		Appointment appointment = appointment(organisationId, doctorUserId);
		OrganisationContextResource owner = actor(
				organisationId, Set.of("DOCTOR"));
		OrganisationContextResource receptionist = actor(
				organisationId, Set.of("RECEPTIONIST"));

		assertDoesNotThrow(() -> policy.requireActorCanTransition(
				appointment, doctorUserId, owner, AppointmentStatus.CONFIRMED));
		assertThrows(SchedulingAccessDeniedException.class, () ->
				policy.requireActorCanTransition(
						appointment,
						UUID.randomUUID(),
						receptionist,
						AppointmentStatus.CONFIRMED));
		assertDoesNotThrow(() -> policy.requireActorCanTransition(
				appointment,
				UUID.randomUUID(),
				receptionist,
				AppointmentStatus.RESCHEDULED));
	}

	@Test
	void operationsCheckInWhileOnlyTheOwningDoctorDeliversCare() {
		UUID organisationId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		Appointment appointment = appointment(organisationId, doctorUserId);
		OrganisationContextResource owner = actor(
				organisationId, Set.of("DOCTOR"));
		OrganisationContextResource receptionist = actor(
				organisationId, Set.of("RECEPTIONIST"));
		UUID receptionistUserId = UUID.randomUUID();

		assertDoesNotThrow(() -> policy.requireActorCanTransition(
				appointment,
				receptionistUserId,
				receptionist,
				AppointmentStatus.CHECKED_IN));
		assertThrows(SchedulingAccessDeniedException.class, () ->
				policy.requireActorCanTransition(
						appointment,
						doctorUserId,
						owner,
						AppointmentStatus.CHECKED_IN));
		assertDoesNotThrow(() -> policy.requireActorCanTransition(
				appointment,
				doctorUserId,
				owner,
				AppointmentStatus.IN_PROGRESS));
		assertDoesNotThrow(() -> policy.requireActorCanTransition(
				appointment,
				doctorUserId,
				owner,
				AppointmentStatus.COMPLETED));
		assertThrows(SchedulingAccessDeniedException.class, () ->
				policy.requireActorCanTransition(
						appointment,
						receptionistUserId,
						receptionist,
						AppointmentStatus.IN_PROGRESS));
	}

	@Test
	void noShowRequiresAnAuthorisedActorAndTheScheduledStart() {
		UUID organisationId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		Appointment appointment = appointment(organisationId, doctorUserId);
		OrganisationContextResource owner = actor(
				organisationId, Set.of("DOCTOR"));
		OrganisationContextResource receptionist = actor(
				organisationId, Set.of("RECEPTIONIST"));

		assertDoesNotThrow(() -> policy.requireActorCanTransition(
				appointment,
				doctorUserId,
				owner,
				AppointmentStatus.NO_SHOW));
		assertDoesNotThrow(() -> policy.requireActorCanTransition(
				appointment,
				UUID.randomUUID(),
				receptionist,
				AppointmentStatus.NO_SHOW));
		assertThrows(
				AppointmentNotReadyForNoShowException.class,
				() -> policy.requireTemporalTransition(
						appointment,
						AppointmentStatus.NO_SHOW,
						appointment.getStartsAt().minusSeconds(1)));
		assertDoesNotThrow(() -> policy.requireTemporalTransition(
				appointment,
				AppointmentStatus.NO_SHOW,
				appointment.getStartsAt()));
	}

	private static Appointment appointment(
			UUID organisationId,
			UUID doctorUserId) {
		return Appointment.request(
				organisationId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				doctorUserId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				Instant.parse("2027-01-04T09:00:00Z"),
				Instant.parse("2027-01-04T09:30:00Z"),
				"UTC",
				"Synthetic room",
				UUID.randomUUID(),
				UUID.randomUUID(),
				Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC));
	}

	private static OrganisationContextResource actor(
			UUID organisationId,
			Set<String> roles) {
		return new OrganisationContextResource(
				UUID.randomUUID(), organisationId, "Synthetic clinic", "CLINIC",
				roles, 0);
	}
}
