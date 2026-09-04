package com.sahha.notification.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommunicationEventV1(
		UUID eventId,
		String eventType,
		Integer schemaVersion,
		Instant occurredAt,
		UUID organisationId,
		UUID conversationId,
		Long resourceVersion,
		UUID messageId,
		UUID senderUserId,
		List<UUID> recipientUserIds) {
	public static final String MESSAGE_SENT = "message.sent.v1";
	public static final String CONVERSATION_CREATED = "conversation.created.v1";
}
