package com.sahha.notification.dto.response;

import java.util.Objects;

public record RealtimeNotificationMessage(
		String messageType,
		NotificationResponse notification) {

	public static final String NOTIFICATION_CREATED = "NOTIFICATION_CREATED";

	public RealtimeNotificationMessage {
		if (!NOTIFICATION_CREATED.equals(messageType)) {
			throw new IllegalArgumentException(
					"realtime notification message type is invalid");
		}
		Objects.requireNonNull(notification);
	}

	public static RealtimeNotificationMessage created(
			NotificationResponse notification) {
		return new RealtimeNotificationMessage(
				NOTIFICATION_CREATED, notification);
	}
}
