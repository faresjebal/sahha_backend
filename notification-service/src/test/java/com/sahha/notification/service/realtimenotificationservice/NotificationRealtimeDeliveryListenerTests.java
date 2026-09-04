package com.sahha.notification.service.realtimenotificationservice;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sahha.notification.event.InAppNotificationCreatedEvent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimeDeliveryListenerTests {

	@Mock
	private NotificationRealtimeDeliveryService deliveryService;

	@Test
	void realtimeFailureCannotRollBackOrRetryCommittedKafkaWork() {
		InAppNotificationCreatedEvent event = new InAppNotificationCreatedEvent(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		when(deliveryService.deliver(event)).thenThrow(
				new IllegalStateException("synthetic broker failure"));

		NotificationRealtimeDeliveryListener listener =
				new NotificationRealtimeDeliveryListener(deliveryService);

		assertDoesNotThrow(() -> listener.afterNotificationCommit(event));
	}
}
