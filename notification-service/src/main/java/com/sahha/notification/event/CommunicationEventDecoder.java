package com.sahha.notification.event;

import java.util.HashSet;
import java.util.Optional;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CommunicationEventDecoder {
	private static final int SCHEMA_VERSION = 1;
	private static final int MAXIMUM_PAYLOAD_LENGTH = 16_384;
	private static final int MAXIMUM_RECIPIENTS = 20;
	private final ObjectMapper objectMapper;

	public CommunicationEventDecoder(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Optional<CommunicationEventV1> decode(String payload) {
		if (payload == null || payload.isBlank()
				|| payload.length() > MAXIMUM_PAYLOAD_LENGTH) {
			throw new InvalidCommunicationEventException("MALFORMED_PAYLOAD");
		}
		CommunicationEventV1 event;
		try {
			event = objectMapper.readValue(payload, CommunicationEventV1.class);
		}
		catch (RuntimeException malformed) {
			throw new InvalidCommunicationEventException("MALFORMED_PAYLOAD");
		}
		validateEnvelope(event);
		if (CommunicationEventV1.CONVERSATION_CREATED.equals(event.eventType())) {
			return Optional.empty();
		}
		validateMessage(event);
		return Optional.of(event);
	}

	private static void validateEnvelope(CommunicationEventV1 event) {
		if (event == null || event.eventId() == null || event.occurredAt() == null
				|| event.organisationId() == null || event.conversationId() == null
				|| event.resourceVersion() == null) {
			throw new InvalidCommunicationEventException("MISSING_REQUIRED_FIELD");
		}
		if (!CommunicationEventV1.MESSAGE_SENT.equals(event.eventType())
				&& !CommunicationEventV1.CONVERSATION_CREATED.equals(event.eventType())) {
			throw new InvalidCommunicationEventException("UNSUPPORTED_EVENT_TYPE");
		}
		if (event.schemaVersion() == null || event.schemaVersion() != SCHEMA_VERSION) {
			throw new InvalidCommunicationEventException("UNSUPPORTED_SCHEMA_VERSION");
		}
		if (event.resourceVersion() < 0) {
			throw new InvalidCommunicationEventException("INVALID_RESOURCE_VERSION");
		}
	}

	private static void validateMessage(CommunicationEventV1 event) {
		if (event.messageId() == null || event.senderUserId() == null
				|| event.recipientUserIds() == null) {
			throw new InvalidCommunicationEventException("MISSING_REQUIRED_FIELD");
		}
		if (event.recipientUserIds().isEmpty()
				|| event.recipientUserIds().size() > MAXIMUM_RECIPIENTS
				|| event.recipientUserIds().stream().anyMatch(value -> value == null)
				|| new HashSet<>(event.recipientUserIds()).size()
						!= event.recipientUserIds().size()
				|| event.recipientUserIds().contains(event.senderUserId())) {
			throw new InvalidCommunicationEventException("INVALID_RECIPIENTS");
		}
	}
}
