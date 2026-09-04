package com.sahha.communication.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RealtimeMessageResponse(
		String messageType,
		UUID messageId,
		UUID conversationId,
		UUID senderUserId,
		String senderDisplayName,
		String body,
		Instant sentAt) {
	public static RealtimeMessageResponse created(
			UUID messageId, UUID conversationId, UUID senderUserId,
			String senderDisplayName, String body, Instant sentAt) {
		return new RealtimeMessageResponse("MESSAGE_CREATED", messageId,
				conversationId, senderUserId, senderDisplayName, body, sentAt);
	}
}
