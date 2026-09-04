package com.sahha.scheduling.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.repository.DoctorAvailabilityScheduleRepository;

@SpringBootTest
class AppointmentConcurrencyIntegrationTests {

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private DoctorAvailabilityScheduleRepository availabilityRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private Clock clock;

	@Test
	void databaseAllowsOnlyOneConcurrentOverlappingDoctorAppointment()
			throws Exception {
		UUID organisationId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		UUID doctorMembershipId = UUID.randomUUID();
		UUID actorUserId = UUID.randomUUID();
		UUID actorMembershipId = UUID.randomUUID();
		LocalDate monday = LocalDate.now(ZoneOffset.UTC)
				.plusDays(1)
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
		Instant startsAt = monday.atTime(9, 0).toInstant(ZoneOffset.UTC);
		Instant endsAt = monday.atTime(9, 30).toInstant(ZoneOffset.UTC);
		DoctorAvailabilitySchedule schedule = transactionTemplate.execute(status ->
				availabilityRepository.saveAndFlush(
						DoctorAvailabilitySchedule.create(
								organisationId,
								doctorUserId,
								doctorMembershipId,
								"UTC",
								30,
								0,
								60,
								"Concurrency room",
								List.of(new DoctorAvailabilitySchedule.WeeklyWindowValue(
										DayOfWeek.MONDAY,
										LocalTime.of(9, 0),
										LocalTime.of(10, 0))),
								List.of(),
								List.of(),
								doctorUserId,
								clock)));

		CyclicBarrier barrier = new CyclicBarrier(2);
		Callable<Boolean> attempt = () -> {
			try {
				return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
					try {
						barrier.await();
					}
					catch (Exception coordinationFailure) {
						throw new IllegalStateException(coordinationFailure);
					}
					appointmentRepository.saveAndFlush(Appointment.request(
							organisationId,
							UUID.randomUUID(),
							UUID.randomUUID(),
							UUID.randomUUID(),
							doctorUserId,
							doctorMembershipId,
							schedule.getId(),
							startsAt,
							endsAt,
							"UTC",
							"Concurrency room",
							actorUserId,
							actorMembershipId,
							clock));
					return true;
				}));
			}
			catch (RuntimeException rejectedOverlap) {
				return false;
			}
		};

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(attempt);
			var second = executor.submit(attempt);
			long accepted = List.of(first.get(), second.get()).stream()
					.filter(Boolean::booleanValue)
					.count();
			assertEquals(1, accepted);
			assertEquals(1, appointmentRepository.countByOrganisationId(
					organisationId));
		}
		finally {
			executor.shutdownNow();
		}
	}
}
