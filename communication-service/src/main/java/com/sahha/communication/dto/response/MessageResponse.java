package com.sahha.communication.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
		UUID id, UUID conversationId, UUID senderUserId,
		String senderDisplayName, String body, Instant sentAt) {
}
