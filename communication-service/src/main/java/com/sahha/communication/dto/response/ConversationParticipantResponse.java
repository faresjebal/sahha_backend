package com.sahha.communication.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConversationParticipantResponse(
		UUID userId, UUID membershipId, String displayName,
		Instant joinedAt, Instant lastReadAt) {
}
