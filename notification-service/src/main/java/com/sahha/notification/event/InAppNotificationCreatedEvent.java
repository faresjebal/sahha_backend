package com.sahha.notification.event;

import java.util.Objects;
import java.util.UUID;

import com.sahha.notification.entity.InAppNotification;

public record InAppNotificationCreatedEvent(
		UUID notificationId,
		UUID organisationId,
		UUID recipientUserId) {

	public InAppNotificationCreatedEvent {
		Objects.requireNonNull(notificationId);
		Objects.requireNonNull(organisationId);
		Objects.requireNonNull(recipientUserId);
	}

	public static InAppNotificationCreatedEvent from(
			InAppNotification notification) {
		return new InAppNotificationCreatedEvent(
				notification.getId(),
				notification.getOrganisationId(),
				notification.getRecipientUserId());
	}
}
