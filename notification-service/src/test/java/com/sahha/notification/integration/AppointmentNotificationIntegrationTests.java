package com.sahha.notification.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import com.sahha.notification.entity.NotificationType;
import com.sahha.notification.entity.ConsumedEventOutcome;
import com.sahha.notification.event.AppointmentEventSource;
import com.sahha.notification.event.AppointmentEventType;
import com.sahha.notification.event.AppointmentEventV1;
import com.sahha.notification.event.AppointmentStatus;
import com.sahha.notification.repository.AppointmentNotificationCursorRepository;
import com.sahha.notification.repository.ConsumedAppointmentEventRepository;
import com.sahha.notification.repository.InAppNotificationRepository;
import com.sahha.notification.repository.RejectedAppointmentEventRepository;
import com.sahha.notification.service.appointmentnotificationservice.AppointmentEventProcessingResult;
import com.sahha.notification.service.appointmentnotificationservice.AppointmentNotificationService;
import com.sahha.notification.service.appointmentnotificationservice.RejectedAppointmentEventService;

@SpringBootTest
@Transactional
class AppointmentNotificationIntegrationTests {

	private static final String TOPIC =
			"sahha.scheduling.appointments.v1";

	@Autowired
	private AppointmentNotificationService service;

	@Autowired
	private ConsumedAppointmentEventRepository consumedRepository;

	@Autowired
	private AppointmentNotificationCursorRepository cursorRepository;

	@Autowired
	private InAppNotificationRepository notificationRepository;

	@Autowired
	private RejectedAppointmentEventRepository rejectedRepository;

	@Autowired
	private RejectedAppointmentEventService rejectedEventService;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void requestedAppointmentCreatesOneMinimumDoctorNotification() {
		UUID appointmentId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		UUID patientId = UUID.randomUUID();
		UUID actorUserId = UUID.randomUUID();
		AppointmentEventV1 event = event(
				UUID.randomUUID(),
				appointmentId,
				doctorUserId,
				organisationId,
				patientId,
				actorUserId,
				AppointmentEventType.APPOINTMENT_REQUESTED,
				AppointmentStatus.REQUESTED,
				0L,
				null,
				null);

		assertEquals(
				AppointmentEventProcessingResult.NOTIFICATION_CREATED,
				service.consume(event, source(0L)));
		assertEquals(
				AppointmentEventProcessingResult.DUPLICATE,
				service.consume(event, source(1L)));

		assertEquals(1, consumedRepository.countByAppointmentId(appointmentId));
		assertEquals(1, notificationRepository.countBySourceEventId(event.eventId()));
		var notification = notificationRepository
				.findAllByRecipientUserIdOrderByCreatedAtDesc(doctorUserId)
				.getFirst();
		assertEquals(appointmentId, notification.getResourceId());
		assertEquals(AppointmentStatus.REQUESTED,
				notification.getAppointmentStatus());
		String serialised = objectMapper.writeValueAsString(notification);
		assertFalse(serialised.contains(patientId.toString()));
		assertFalse(serialised.contains(actorUserId.toString()));
		assertFalse(serialised.contains("notification-processing-test"));
	}

	@Test
	void anOlderAggregateVersionIsRecordedWithoutCreatingAStaleNotification() {
		UUID appointmentId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		AppointmentEventV1 current = event(
				UUID.randomUUID(),
				appointmentId,
				doctorUserId,
				organisationId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				AppointmentEventType.APPOINTMENT_RESCHEDULED,
				AppointmentStatus.RESCHEDULED,
				2L,
				AppointmentStatus.CONFIRMED,
				Instant.parse("2027-01-04T09:00:00Z"));
		AppointmentEventV1 stale = event(
				UUID.randomUUID(),
				appointmentId,
				doctorUserId,
				organisationId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				AppointmentEventType.APPOINTMENT_CONFIRMED,
				AppointmentStatus.CONFIRMED,
				1L,
				AppointmentStatus.REQUESTED,
				Instant.parse("2027-01-04T09:00:00Z"));

		assertEquals(
				AppointmentEventProcessingResult.NOTIFICATION_CREATED,
				service.consume(current, source(2L)));
		assertEquals(
				AppointmentEventProcessingResult.STALE,
				service.consume(stale, source(3L)));

		assertEquals(2, consumedRepository.countByAppointmentId(appointmentId));
		assertEquals(
				ConsumedEventOutcome.STALE,
				consumedRepository.findById(stale.eventId()).orElseThrow()
						.getOutcome());
		assertEquals(
				2L,
				cursorRepository.findById(appointmentId).orElseThrow()
						.getLastResourceVersion());
		assertEquals(1, notificationRepository.findAll().size());
	}

	@Test
	void doctorAuthoredDecisionIsConsumedWithoutInventingAPatientRecipient() {
		UUID appointmentId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		AppointmentEventV1 event = event(
				UUID.randomUUID(),
				appointmentId,
				doctorUserId,
				organisationId,
				UUID.randomUUID(),
				doctorUserId,
				AppointmentEventType.APPOINTMENT_CONFIRMED,
				AppointmentStatus.CONFIRMED,
				1L,
				AppointmentStatus.REQUESTED,
				Instant.parse("2027-01-04T09:00:00Z"));

		assertEquals(
				AppointmentEventProcessingResult.NO_ELIGIBLE_RECIPIENT,
				service.consume(event, source(4L)));

		assertEquals(
				ConsumedEventOutcome.NO_ELIGIBLE_RECIPIENT,
				consumedRepository.findById(event.eventId()).orElseThrow()
						.getOutcome());
		assertEquals(0, notificationRepository.count());
	}

	@Test
	void receptionistCheckInCreatesOneMinimumDoctorNotification() {
		UUID appointmentId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		AppointmentEventV1 event = event(
				UUID.randomUUID(),
				appointmentId,
				doctorUserId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				AppointmentEventType.APPOINTMENT_CHECKED_IN,
				AppointmentStatus.CHECKED_IN,
				2L,
				AppointmentStatus.CONFIRMED,
				Instant.parse("2027-01-04T09:30:00Z"));

		assertEquals(
				AppointmentEventProcessingResult.NOTIFICATION_CREATED,
				service.consume(event, source(6L)));

		var notification = notificationRepository
				.findAllByRecipientUserIdOrderByCreatedAtDesc(doctorUserId)
				.getFirst();
		assertEquals(
				NotificationType.PATIENT_CHECKED_IN,
				notification.getNotificationType());
		assertEquals(
				AppointmentStatus.CHECKED_IN,
				notification.getAppointmentStatus());
		assertEquals(appointmentId, notification.getResourceId());
	}

	@Test
	void doctorAuthoredCareDeliveryEventsAreConsumedWithoutSelfNotification() {
		UUID appointmentId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		AppointmentEventV1 started = event(
				UUID.randomUUID(),
				appointmentId,
				doctorUserId,
				organisationId,
				UUID.randomUUID(),
				doctorUserId,
				AppointmentEventType.APPOINTMENT_STARTED,
				AppointmentStatus.IN_PROGRESS,
				3L,
				AppointmentStatus.CHECKED_IN,
				Instant.parse("2027-01-04T09:30:00Z"));

		assertEquals(
				AppointmentEventProcessingResult.NO_ELIGIBLE_RECIPIENT,
				service.consume(started, source(7L)));
		assertEquals(0, notificationRepository.count());
	}

	@Test
	void rejectedPayloadIsFingerprintOnlyAndItsKafkaPositionIsIdempotent() {
		AppointmentEventSource source = source(5L);
		String rawPayload = "synthetic-sensitive-payload";

		assertTrue(rejectedEventService.record(
				source, rawPayload, "MALFORMED_PAYLOAD"));
		assertFalse(rejectedEventService.record(
				source, rawPayload, "MALFORMED_PAYLOAD"));

		var rejected = rejectedRepository.findAll().getFirst();
		assertEquals("MALFORMED_PAYLOAD", rejected.getReasonCode());
		assertEquals(64, rejected.getPayloadSha256().length());
		assertFalse(rejected.getPayloadSha256().contains(rawPayload));
	}

	private static AppointmentEventV1 event(
			UUID eventId,
			UUID appointmentId,
			UUID doctorUserId,
			UUID organisationId,
			UUID patientId,
			UUID actorUserId,
			AppointmentEventType eventType,
			AppointmentStatus status,
			long resourceVersion,
			AppointmentStatus previousStatus,
			Instant previousStartsAt) {
		return new AppointmentEventV1(
				eventId,
				eventType,
				1,
				Instant.parse("2027-01-01T00:00:00Z"),
				appointmentId,
				organisationId,
				patientId,
				doctorUserId,
				actorUserId,
				"notification-processing-test",
				status,
				Instant.parse("2027-01-04T09:30:00Z"),
				Instant.parse("2027-01-04T10:00:00Z"),
				"UTC",
				"Synthetic notification room",
				resourceVersion,
				previousStatus,
				previousStartsAt);
	}

	private static AppointmentEventSource source(long offset) {
		return new AppointmentEventSource(TOPIC, 0, offset);
	}
}
