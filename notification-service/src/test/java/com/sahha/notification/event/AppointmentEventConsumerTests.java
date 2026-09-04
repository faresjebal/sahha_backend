package com.sahha.notification.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sahha.notification.service.appointmentnotificationservice.AppointmentEventProcessingResult;
import com.sahha.notification.service.appointmentnotificationservice.AppointmentNotificationService;
import com.sahha.notification.service.appointmentnotificationservice.RejectedAppointmentEventService;

@ExtendWith(MockitoExtension.class)
class AppointmentEventConsumerTests {

	@Mock
	private AppointmentEventDecoder decoder;

	@Mock
	private AppointmentNotificationService notificationService;

	@Mock
	private RejectedAppointmentEventService rejectedEventService;

	private AppointmentEventConsumer consumer;

	@BeforeEach
	void setUp() {
		consumer = new AppointmentEventConsumer(
				decoder, notificationService, rejectedEventService);
	}

	@Test
	void passesValidatedEventAndKafkaPositionToTheTransactionalService() {
		AppointmentEventV1 event = event();
		ConsumerRecord<String, String> record = new ConsumerRecord<>(
				"sahha.scheduling.appointments.v1",
				2,
				17L,
				event.appointmentId().toString(),
				"synthetic-payload");
		when(decoder.decode("synthetic-payload")).thenReturn(event);
		when(notificationService.consume(
				event,
				new AppointmentEventSource(
						"sahha.scheduling.appointments.v1", 2, 17L)))
				.thenReturn(AppointmentEventProcessingResult.NOTIFICATION_CREATED);

		consumer.consume(record);

		verify(notificationService).consume(
				event,
				new AppointmentEventSource(
						"sahha.scheduling.appointments.v1", 2, 17L));
	}

	@Test
	void recordsAKeyMismatchWithoutPassingItToNotificationProjection() {
		AppointmentEventV1 event = event();
		ConsumerRecord<String, String> record = new ConsumerRecord<>(
				"sahha.scheduling.appointments.v1",
				0,
				0L,
				UUID.randomUUID().toString(),
				"synthetic-payload");
		when(decoder.decode("synthetic-payload")).thenReturn(event);

		consumer.consume(record);

		verifyNoInteractions(notificationService);
		verify(rejectedEventService).record(
				new AppointmentEventSource(
						"sahha.scheduling.appointments.v1", 0, 0L),
				"synthetic-payload",
				"APPOINTMENT_KEY_MISMATCH");
	}

	private static AppointmentEventV1 event() {
		return new AppointmentEventV1(
				UUID.randomUUID(),
				AppointmentEventType.APPOINTMENT_REQUESTED,
				1,
				Instant.parse("2027-01-01T00:00:00Z"),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"notification-consumer-test",
				AppointmentStatus.REQUESTED,
				Instant.parse("2027-01-04T09:00:00Z"),
				Instant.parse("2027-01-04T09:30:00Z"),
				"UTC",
				"Synthetic notification room",
				0L,
				null,
				null);
	}
}
