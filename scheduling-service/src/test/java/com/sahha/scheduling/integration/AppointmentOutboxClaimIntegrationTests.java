package com.sahha.scheduling.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sahha.scheduling.config.SchedulingOutboxProperties;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentAuditEvent;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;
import com.sahha.scheduling.outbox.AppointmentOutboxClaimService;
import com.sahha.scheduling.outbox.AppointmentOutboxRecorder;
import com.sahha.scheduling.outbox.ClaimedAppointmentOutboxEvent;
import com.sahha.scheduling.repository.AppointmentAuditEventRepository;
import com.sahha.scheduling.repository.AppointmentOutboxEventRepository;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.repository.DoctorAvailabilityScheduleRepository;

@SpringBootTest
class AppointmentOutboxClaimIntegrationTests {

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentAuditEventRepository auditRepository;

	@Autowired
	private AppointmentOutboxEventRepository outboxRepository;

	@Autowired
	private DoctorAvailabilityScheduleRepository availabilityRepository;

	@Autowired
	private AppointmentOutboxRecorder recorder;

	@Autowired
	private AppointmentOutboxClaimService claimService;

	@Autowired
	private SchedulingOutboxProperties properties;

	@Autowired
	private Clock clock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private Fixture fixture;

	@AfterEach
	void cleanFixture() {
		if (fixture == null) {
			return;
		}
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			outboxRepository.deleteById(fixture.outboxEventId());
			outboxRepository.flush();
			auditRepository.deleteById(fixture.auditEventId());
			auditRepository.flush();
			appointmentRepository.deleteById(fixture.appointmentId());
			appointmentRepository.flush();
			availabilityRepository.deleteById(fixture.scheduleId());
		});
	}

	@Test
	void activeLeasePreventsSecondClaimAndExpiredLeaseCanBeRecovered() {
		fixture = createFixture();
		Instant firstObservedAt = fixture.occurredAt().plusSeconds(1);

		List<ClaimedAppointmentOutboxEvent> first =
				claimService.claimReady(firstObservedAt);
		List<ClaimedAppointmentOutboxEvent> blocked =
				claimService.claimReady(firstObservedAt.plusSeconds(1));
		List<ClaimedAppointmentOutboxEvent> recovered = claimService.claimReady(
				firstObservedAt.plus(properties.claimLease()).plusSeconds(1));

		assertEquals(1, first.size());
		assertTrue(blocked.isEmpty());
		assertEquals(1, recovered.size());
		assertEquals(first.getFirst().eventId(), recovered.getFirst().eventId());
		assertNotEquals(
				first.getFirst().claimToken(), recovered.getFirst().claimToken());
		assertFalse(claimService.markPublished(
				first.getFirst().eventId(),
				first.getFirst().claimToken(),
				firstObservedAt.plus(properties.claimLease()).plusSeconds(2)));
		assertTrue(claimService.markPublished(
				recovered.getFirst().eventId(),
				recovered.getFirst().claimToken(),
				firstObservedAt.plus(properties.claimLease()).plusSeconds(2)));
		assertNotNull(outboxRepository.findById(fixture.outboxEventId())
				.orElseThrow().getPublishedAt());
	}

	private Fixture createFixture() {
		return new TransactionTemplate(transactionManager).execute(status -> {
			UUID organisationId = UUID.randomUUID();
			UUID doctorUserId = UUID.randomUUID();
			UUID doctorMembershipId = UUID.randomUUID();
			Instant occurredAt = clock.instant();
			LocalDate appointmentDate = LocalDate.now(ZoneOffset.UTC).plusDays(7);
			DoctorAvailabilitySchedule schedule =
					availabilityRepository.saveAndFlush(
							DoctorAvailabilitySchedule.create(
									organisationId,
									doctorUserId,
									doctorMembershipId,
									"UTC",
									30,
									0,
									60,
									"Synthetic outbox room",
									List.of(new DoctorAvailabilitySchedule
											.WeeklyWindowValue(
											appointmentDate.getDayOfWeek(),
											LocalTime.of(9, 0),
											LocalTime.of(10, 0))),
									List.of(),
									List.of(),
									doctorUserId,
									clock));
			Instant startsAt = appointmentDate.atTime(9, 0)
					.toInstant(ZoneOffset.UTC);
			UUID actorUserId = UUID.randomUUID();
			UUID actorMembershipId = UUID.randomUUID();
			Appointment appointment = appointmentRepository.saveAndFlush(
					Appointment.request(
							organisationId,
							UUID.randomUUID(),
							UUID.randomUUID(),
							UUID.randomUUID(),
							doctorUserId,
							doctorMembershipId,
							schedule.getId(),
							startsAt,
							startsAt.plusSeconds(1_800),
							"UTC",
							"Synthetic outbox room",
							actorUserId,
							actorMembershipId,
							clock));
			AppointmentAuditEvent audit = auditRepository.saveAndFlush(
					AppointmentAuditEvent.booked(
							appointment,
							actorUserId,
							actorMembershipId,
							"outbox-claim-integration"));
			var outbox = recorder.record(appointment, audit);
			outboxRepository.flush();
			return new Fixture(
					schedule.getId(), appointment.getId(), audit.getId(),
					outbox.getId(), occurredAt);
		});
	}

	private record Fixture(
			UUID scheduleId,
			UUID appointmentId,
			UUID auditEventId,
			UUID outboxEventId,
			Instant occurredAt) {
	}
}
