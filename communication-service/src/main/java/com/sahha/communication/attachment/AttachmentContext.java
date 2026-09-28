package com.sahha.communication.attachment;
import java.util.UUID;
/** Conversation authority only. Contains no patient or clinical identifiers. */
public record AttachmentContext(UUID conversationId, UUID organisationId, UUID actorUserId,
        UUID fileId, UUID messageId, UUID messageRequestId, UUID uploaderUserId) { }
