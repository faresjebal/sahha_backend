package com.sahha.notification.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class CommunicationEventDecoderTests {
	@Autowired private CommunicationEventDecoder decoder;
	@Autowired private ObjectMapper objectMapper;

	@Test
	void decodesThePrivacyMinimisedMessageContract() {
		CommunicationEventV1 event = messageEvent(List.of(UUID.randomUUID()));
		assertEquals(event, decoder.decode(
				objectMapper.writeValueAsString(event)).orElseThrow());
	}

	@Test
	void ignoresKnownConversationCreatedEvents() {
		CommunicationEventV1 event = new CommunicationEventV1(
				UUID.randomUUID(), CommunicationEventV1.CONVERSATION_CREATED, 1,
				Instant.parse("2027-01-01T00:00:00Z"), UUID.randomUUID(),
				UUID.randomUUID(), 0L, null, null, null);
		assertTrue(decoder.decode(objectMapper.writeValueAsString(event)).isEmpty());
	}

	@Test
	void rejectsSenderRecipientsAndDuplicateRecipients() {
		UUID sender = UUID.randomUUID();
		CommunicationEventV1 senderRecipient = event(sender, List.of(sender));
		assertEquals("INVALID_RECIPIENTS", assertThrows(
				InvalidCommunicationEventException.class,
				() -> decoder.decode(objectMapper.writeValueAsString(senderRecipient)))
				.getReasonCode());

		UUID recipient = UUID.randomUUID();
		CommunicationEventV1 duplicate = event(sender, List.of(recipient, recipient));
		assertEquals("INVALID_RECIPIENTS", assertThrows(
				InvalidCommunicationEventException.class,
				() -> decoder.decode(objectMapper.writeValueAsString(duplicate)))
				.getReasonCode());
	}

	private static CommunicationEventV1 messageEvent(List<UUID> recipients) {
		return event(UUID.randomUUID(), recipients);
	}

	private static CommunicationEventV1 event(UUID sender, List<UUID> recipients) {
		return new CommunicationEventV1(
				UUID.randomUUID(), CommunicationEventV1.MESSAGE_SENT, 1,
				Instant.parse("2027-01-01T00:00:00Z"), UUID.randomUUID(),
				UUID.randomUUID(), 1L, UUID.randomUUID(), sender, recipients);
	}
}
