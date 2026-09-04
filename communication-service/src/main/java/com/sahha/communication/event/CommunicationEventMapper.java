package com.sahha.communication.event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sahha.communication.entity.CommunicationAuditEvent;
import com.sahha.communication.entity.ConversationMessage;
import com.sahha.communication.entity.ConversationThread;

@Component
public class CommunicationEventMapper {
	public Map<String,Object> conversationCreated(CommunicationAuditEvent audit,
			ConversationThread thread, List<UUID> participantUserIds) {
		Map<String,Object> payload = base(audit, "conversation.created.v1", thread);
		payload.put("participantUserIds", participantUserIds.stream().map(UUID::toString).toList());
		return Map.copyOf(payload);
	}

	public Map<String,Object> messageSent(CommunicationAuditEvent audit,
			ConversationThread thread, ConversationMessage message,
			List<UUID> recipientUserIds) {
		Map<String,Object> payload = base(audit, "message.sent.v1", thread);
		payload.put("messageId", message.getId().toString());
		payload.put("senderUserId", message.getSenderUserId().toString());
		payload.put("recipientUserIds", recipientUserIds.stream().map(UUID::toString).toList());
		return Map.copyOf(payload);
	}

	private static Map<String,Object> base(CommunicationAuditEvent audit,
			String type, ConversationThread thread) {
		Map<String,Object> payload = new LinkedHashMap<>();
		payload.put("eventId", audit.getId().toString());
		payload.put("eventType", type);
		payload.put("schemaVersion", 1);
		payload.put("occurredAt", audit.getOccurredAt().toString());
		payload.put("organisationId", thread.getOrganisationId().toString());
		payload.put("conversationId", thread.getId().toString());
		payload.put("resourceVersion", audit.getResourceVersion());
		return payload;
	}
}
