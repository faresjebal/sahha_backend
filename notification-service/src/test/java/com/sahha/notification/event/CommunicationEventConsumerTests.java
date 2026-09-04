package com.sahha.notification.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sahha.notification.service.messagenotificationservice.MessageNotificationService;
import com.sahha.notification.service.messagenotificationservice.RejectedCommunicationEventService;

@ExtendWith(MockitoExtension.class)
class CommunicationEventConsumerTests {
	@Mock private CommunicationEventDecoder decoder;
	@Mock private MessageNotificationService notificationService;
	@Mock private RejectedCommunicationEventService rejectedEventService;
	private CommunicationEventConsumer consumer;

	@BeforeEach
	void setUp() {
		consumer = new CommunicationEventConsumer(
				decoder, notificationService, rejectedEventService);
	}

	@Test
	void passesAMessageAndItsKafkaPositionToTheTransactionalService() {
		CommunicationEventV1 event = event();
		var record = record(event.conversationId().toString());
		when(decoder.decode("synthetic-routing-payload"))
				.thenReturn(Optional.of(event));

		consumer.consume(record);

		verify(notificationService).consume(event,
				new CommunicationEventSource(
						"sahha.communication.messages.v1", 1, 9L));
	}

	@Test
	void recordsAConversationKeyMismatchWithoutCreatingANotification() {
		CommunicationEventV1 event = event();
		when(decoder.decode("synthetic-routing-payload"))
				.thenReturn(Optional.of(event));

		consumer.consume(record(UUID.randomUUID().toString()));

		verifyNoInteractions(notificationService);
		verify(rejectedEventService).record(
				new CommunicationEventSource(
						"sahha.communication.messages.v1", 1, 9L),
				"synthetic-routing-payload", "CONVERSATION_KEY_MISMATCH");
	}

	private static ConsumerRecord<String,String> record(String key) {
		return new ConsumerRecord<>("sahha.communication.messages.v1", 1, 9L,
				key, "synthetic-routing-payload");
	}

	private static CommunicationEventV1 event() {
		return new CommunicationEventV1(
				UUID.randomUUID(), CommunicationEventV1.MESSAGE_SENT, 1,
				Instant.parse("2027-01-01T00:00:00Z"), UUID.randomUUID(),
				UUID.randomUUID(), 1L, UUID.randomUUID(), UUID.randomUUID(),
				List.of(UUID.randomUUID()));
	}
}
