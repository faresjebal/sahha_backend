package com.sahha.scheduling.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sahha.scheduling.config.SchedulingOutboxProperties;
import com.sahha.scheduling.event.AppointmentDomainEventType;
import com.sahha.scheduling.event.AppointmentEventMapper;

class AppointmentOutboxEventTests {

	private static final Instant OCCURRED_AT =
			Instant.parse("2027-01-01T00:00:00Z");

	@Test
	void failedClaimCanBeRetriedThenPublishedByItsCurrentOwner() {
		AppointmentOutboxEvent outbox = outbox();
		UUID firstClaim = UUID.randomUUID();
		Instant firstAttempt = OCCURRED_AT.plusSeconds(1);
		outbox.claim(firstClaim, firstAttempt, Duration.ofMinutes(5));
		assertThrows(IllegalStateException.class, () -> outbox.claim(
				UUID.randomUUID(),
				firstAttempt.plusSeconds(1),
				Duration.ofMinutes(5)));
		outbox.markFailed(
				firstClaim,
				firstAttempt,
				Duration.ofSeconds(5),
				"SyntheticFailure");

		assertEquals(1, outbox.getPublicationAttempts());
		assertEquals(firstAttempt.plusSeconds(5), outbox.getNextAttemptAt());
		assertNull(outbox.getClaimToken());

		UUID secondClaim = UUID.randomUUID();
		Instant secondAttempt = firstAttempt.plusSeconds(6);
		outbox.claim(secondClaim, secondAttempt, Duration.ofMinutes(5));
		assertThrows(IllegalStateException.class, () ->
				outbox.markPublished(firstClaim, secondAttempt));
		outbox.markPublished(secondClaim, secondAttempt);

		assertEquals(secondAttempt, outbox.getPublishedAt());
		assertEquals(2, outbox.getPublicationAttempts());
		assertNull(outbox.getLastErrorCode());
		assertNull(outbox.getClaimToken());
	}

	@Test
	void propertiesRequireLeaseLongerThanWorstCaseBatchSendWindow() {
		assertThrows(IllegalArgumentException.class, () ->
				new SchedulingOutboxProperties(
						true,
						"sahha.scheduling.appointments.v1",
						10,
						Duration.ofSeconds(1),
						Duration.ofSeconds(5),
						Duration.ofSeconds(50),
						Duration.ofSeconds(5),
						Duration.ofMinutes(5)));
	}

	private static AppointmentOutboxEvent outbox() {
		Clock clock = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);
		Appointment appointment = Appointment.request(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(),
				Instant.parse("2027-01-04T09:00:00Z"),
				Instant.parse("2027-01-04T09:30:00Z"),
				"UTC", "Synthetic room", UUID.randomUUID(), UUID.randomUUID(),
				clock);
		AppointmentAuditEvent audit = AppointmentAuditEvent.booked(
				appointment,
				appointment.getBookedByUserId(),
				appointment.getBookedByMembershipId(),
				"outbox-entity-test");
		UUID eventId = UUID.randomUUID();
		Map<String, Object> payload = new AppointmentEventMapper().toPayload(
				eventId,
				AppointmentDomainEventType.APPOINTMENT_REQUESTED,
				appointment,
				audit);
		return AppointmentOutboxEvent.pending(
				eventId,
				audit,
				appointment,
				AppointmentDomainEventType.APPOINTMENT_REQUESTED,
				payload);
	}
}
