package com.sahha.notification.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.notification.entity.NotificationType;
import com.sahha.notification.event.CommunicationEventSource;
import com.sahha.notification.event.CommunicationEventV1;
import com.sahha.notification.repository.ConsumedCommunicationEventRepository;
import com.sahha.notification.repository.InAppNotificationRepository;
import com.sahha.notification.service.messagenotificationservice.CommunicationEventProcessingResult;
import com.sahha.notification.service.messagenotificationservice.MessageNotificationService;

@SpringBootTest
@Transactional
class MessageNotificationIntegrationTests {
	@Autowired private MessageNotificationService service;
	@Autowired private ConsumedCommunicationEventRepository consumedRepository;
	@Autowired private InAppNotificationRepository notificationRepository;

	@Test
	void createsOneRecoverableRoutingAlertPerRecipientAndIsIdempotent() {
		UUID recipient = UUID.randomUUID();
		UUID conversation = UUID.randomUUID();
		CommunicationEventV1 event = new CommunicationEventV1(
				UUID.randomUUID(), CommunicationEventV1.MESSAGE_SENT, 1,
				Instant.parse("2027-01-01T00:00:00Z"), UUID.randomUUID(),
				conversation, 2L, UUID.randomUUID(), UUID.randomUUID(),
				List.of(recipient));
		CommunicationEventSource first = new CommunicationEventSource(
				"sahha.communication.messages.v1", 0, 11L);

		assertEquals(CommunicationEventProcessingResult.NOTIFICATIONS_CREATED,
				service.consume(event, first));
		assertEquals(CommunicationEventProcessingResult.DUPLICATE,
				service.consume(event, new CommunicationEventSource(
						"sahha.communication.messages.v1", 0, 12L)));

		assertEquals(1, consumedRepository.count());
		var notification = notificationRepository
				.findAllByRecipientUserIdOrderByCreatedAtDesc(recipient).getFirst();
		assertEquals(NotificationType.MESSAGE_RECEIVED,
				notification.getNotificationType());
		assertEquals("CONVERSATION", notification.getResourceType());
		assertEquals(conversation, notification.getResourceId());
		assertNull(notification.getAppointmentStatus());
		assertNull(notification.getAppointmentStartsAt());
		assertNull(notification.getAppointmentLocationLabel());
	}
}
