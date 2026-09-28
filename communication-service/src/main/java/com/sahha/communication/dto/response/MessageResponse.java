package com.sahha.communication.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
		UUID id, UUID conversationId, UUID senderUserId,
		String senderDisplayName, String body, Instant sentAt,
        java.util.List<com.sahha.communication.attachment.MessageAttachmentResponse> attachments) {
    public MessageResponse { attachments=java.util.List.copyOf(attachments); }
    public MessageResponse(UUID id,UUID conversationId,UUID senderUserId,String senderDisplayName,String body,Instant sentAt) {
        this(id,conversationId,senderUserId,senderDisplayName,body,sentAt,java.util.List.of());
    }
}
