package com.sahha.communication.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Internal after-commit signal for connected conversation participants. */
public record CommunicationMessageCreatedEvent(
		UUID messageId,
		UUID conversationId,
		UUID organisationId,
		UUID senderUserId,
		String senderDisplayName,
		String body,
		Instant sentAt,
		List<UUID> recipientUserIds) {
	public CommunicationMessageCreatedEvent {
		recipientUserIds = List.copyOf(recipientUserIds);
	}
}
