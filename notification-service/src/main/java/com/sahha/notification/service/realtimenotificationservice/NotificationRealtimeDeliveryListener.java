package com.sahha.notification.service.realtimenotificationservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sahha.notification.event.InAppNotificationCreatedEvent;

@Component
public class NotificationRealtimeDeliveryListener {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(NotificationRealtimeDeliveryListener.class);
	private final NotificationRealtimeDeliveryService deliveryService;

	public NotificationRealtimeDeliveryListener(
			NotificationRealtimeDeliveryService deliveryService) {
		this.deliveryService = deliveryService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void afterNotificationCommit(InAppNotificationCreatedEvent event) {
		try {
			if (!deliveryService.deliver(event)) {
				LOGGER.warn(
						"Committed notification was unavailable for realtime delivery notificationId={}",
						event.notificationId());
			}
		}
		catch (RuntimeException deliveryFailure) {
			LOGGER.warn(
					"Realtime notification delivery failed notificationId={} exception={}",
					event.notificationId(),
					deliveryFailure.getClass().getSimpleName());
		}
	}
}
