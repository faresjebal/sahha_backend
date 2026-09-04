package com.sahha.notification.service.realtimenotificationservice;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.sahha.notification.config.NotificationWebSocketConfiguration;
import com.sahha.notification.dto.response.RealtimeNotificationMessage;
import com.sahha.notification.entity.NotificationType;
import com.sahha.notification.entity.InAppNotification;
import com.sahha.notification.event.AppointmentEventType;
import com.sahha.notification.event.AppointmentEventV1;
import com.sahha.notification.event.AppointmentStatus;
import com.sahha.notification.event.InAppNotificationCreatedEvent;
import com.sahha.notification.mapper.NotificationMapper;
import com.sahha.notification.repository.InAppNotificationRepository;
import com.sahha.notification.security.NotificationPrincipalName;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimeDeliveryServiceTests {

	@Mock
	private InAppNotificationRepository notificationRepository;

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	private NotificationRealtimeDeliveryService deliveryService;

	@BeforeEach
	void setUp() {
		deliveryService = new NotificationRealtimeDeliveryService(
				notificationRepository,
				new NotificationMapper(),
				messagingTemplate);
	}

	@Test
	void committedNotificationIsSentOnlyToItsOrganisationBoundPrincipal() {
		InAppNotification notification = notification();
		InAppNotificationCreatedEvent event =
				InAppNotificationCreatedEvent.from(notification);
		when(notificationRepository
				.findByIdAndOrganisationIdAndRecipientUserId(
						event.notificationId(),
						event.organisationId(),
						event.recipientUserId()))
				.thenReturn(Optional.of(notification));

		assertTrue(deliveryService.deliver(event));

		verify(messagingTemplate).convertAndSendToUser(
				NotificationPrincipalName.of(
						event.recipientUserId(), event.organisationId()),
				NotificationWebSocketConfiguration.DELIVERY_DESTINATION,
				RealtimeNotificationMessage.created(
						new NotificationMapper().toResponse(notification)));
	}

	@Test
	void missingOrMismatchedNotificationIsNotSent() {
		InAppNotification notification = notification();
		InAppNotificationCreatedEvent event =
				InAppNotificationCreatedEvent.from(notification);
		when(notificationRepository
				.findByIdAndOrganisationIdAndRecipientUserId(
						event.notificationId(),
						event.organisationId(),
						event.recipientUserId()))
				.thenReturn(Optional.empty());

		assertFalse(deliveryService.deliver(event));
		verifyNoInteractions(messagingTemplate);
	}

	private static InAppNotification notification() {
		Instant startsAt = Instant.parse("2027-01-04T09:30:00Z");
		AppointmentEventV1 event = new AppointmentEventV1(
				UUID.randomUUID(),
				AppointmentEventType.APPOINTMENT_REQUESTED,
				1,
				Instant.parse("2027-01-01T00:00:00Z"),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"realtime-delivery-test",
				AppointmentStatus.REQUESTED,
				startsAt,
				startsAt.plusSeconds(1_800),
				"UTC",
				"Synthetic consultation room",
				0L,
				null,
				null);
		return InAppNotification.forDoctor(
				event,
				NotificationType.APPOINTMENT_REQUESTED,
				Instant.parse("2027-01-01T00:00:01Z"));
	}
}
