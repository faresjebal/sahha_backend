package com.sahha.communication.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
		UUID id,
		UUID organisationId,
		String subject,
		UUID patientRegistrationId,
		boolean patientAccessGranted,
		UUID createdByUserId,
		Instant createdAt,
		Instant lastMessageAt,
		long version,
		long unreadCount,
		List<ConversationParticipantResponse> participants) {
	public ConversationResponse { participants = List.copyOf(participants); }
}
