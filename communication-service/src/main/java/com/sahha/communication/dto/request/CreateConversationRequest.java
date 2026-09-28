package com.sahha.communication.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
		@NotNull UUID conversationRequestId,
		@NotNull UUID recipientUserId,
		@NotBlank @Size(min = 4, max = 160) String subject,
		UUID patientRegistrationId,
		UUID sourceConsultationId) {
	public CreateConversationRequest(UUID conversationRequestId, UUID recipientUserId,
			String subject, UUID patientRegistrationId) {
		this(conversationRequestId, recipientUserId, subject, patientRegistrationId, null);
	}
}
