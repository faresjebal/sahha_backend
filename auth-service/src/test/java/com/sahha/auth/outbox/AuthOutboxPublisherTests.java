package com.sahha.auth.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.sahha.auth.config.AuthOutboxProperties;
import com.sahha.auth.entity.AuthOutboxEvent;
import com.sahha.auth.entity.SecurityEvent;
import com.sahha.auth.entity.SecurityEventResult;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.repository.AuthOutboxEventRepository;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthOutboxPublisherTests {

	private static final Instant OCCURRED_AT =
			Instant.parse("2026-07-29T20:00:00Z");
	private static final Instant ATTEMPTED_AT =
			OCCURRED_AT.plusSeconds(1);

	@Mock
	private AuthOutboxEventRepository repository;

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@Mock
	private ObjectMapper objectMapper;

	private AuthOutboxPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new AuthOutboxPublisher(
				repository,
				kafkaTemplate,
				objectMapper,
				new AuthOutboxProperties(
						true,
						"sahha.auth.security-events.v1",
						50,
						Duration.ofSeconds(1),
						Duration.ofSeconds(5),
						Duration.ofSeconds(5),
						Duration.ofMinutes(5)),
				Clock.fixed(ATTEMPTED_AT, ZoneOffset.UTC));
	}

	@Test
	void successfulKafkaAcknowledgementMarksTheRowPublished()
			throws Exception {
		AuthOutboxEvent outbox = outbox();
		when(repository.findReadyForPublication(any(), any()))
				.thenReturn(java.util.List.of(outbox));
		when(objectMapper.writeValueAsString(any()))
				.thenReturn("{\"eventType\":\"LOGIN_SUCCEEDED\"}");
		when(kafkaTemplate.send(
				"sahha.auth.security-events.v1",
				outbox.getSecurityEventId().toString(),
				"{\"eventType\":\"LOGIN_SUCCEEDED\"}"))
				.thenReturn(CompletableFuture.completedFuture(null));

		assertEquals(1, publisher.publishReady());

		assertEquals(ATTEMPTED_AT, outbox.getPublishedAt());
		assertEquals(1, outbox.getPublicationAttempts());
		assertNull(outbox.getLastErrorCode());
		verify(kafkaTemplate).send(
				"sahha.auth.security-events.v1",
				outbox.getSecurityEventId().toString(),
				"{\"eventType\":\"LOGIN_SUCCEEDED\"}");
	}

	@Test
	void failedKafkaAcknowledgementSchedulesABoundedRetry()
			throws Exception {
		AuthOutboxEvent outbox = outbox();
		when(repository.findReadyForPublication(any(), any()))
				.thenReturn(java.util.List.of(outbox));
		when(objectMapper.writeValueAsString(any())).thenReturn("{}");
		when(kafkaTemplate.send(
				"sahha.auth.security-events.v1",
				outbox.getSecurityEventId().toString(),
				"{}"))
				.thenReturn(CompletableFuture.failedFuture(
						new IllegalStateException("synthetic outage")));

		assertEquals(1, publisher.publishReady());

		assertNull(outbox.getPublishedAt());
		assertEquals(1, outbox.getPublicationAttempts());
		assertNotNull(outbox.getLastErrorCode());
		assertEquals(
				ATTEMPTED_AT.plusSeconds(5),
				outbox.getNextAttemptAt());
	}

	private static AuthOutboxEvent outbox() {
		SecurityEvent event = SecurityEvent.create(
				null,
				null,
				null,
				SecurityEventType.LOGIN_SUCCEEDED,
				SecurityEventResult.SUCCESS,
				null,
				null,
				null,
				"request-12345",
				"192.0.2.10",
				"Synthetic browser",
				OCCURRED_AT);
		return AuthOutboxEvent.pending(
				event,
				Map.of(
						"eventId",
						event.getId().toString(),
						"eventType",
						event.getEventType().name()));
	}
}
