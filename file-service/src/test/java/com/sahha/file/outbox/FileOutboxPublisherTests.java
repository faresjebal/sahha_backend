package com.sahha.file.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import com.sahha.file.config.FileOutboxProperties;

@ExtendWith(MockitoExtension.class)
class FileOutboxPublisherTests {

	private static final Instant ATTEMPTED_AT =
			Instant.parse("2026-08-30T00:00:00Z");
	private static final String TOPIC = "sahha.file.medical-files.v1";

	@Mock private FileOutboxClaimService claimService;
	@Mock private KafkaTemplate<String, String> kafkaTemplate;
	@Mock private ObjectMapper objectMapper;
	private FileOutboxPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new FileOutboxPublisher(
				claimService, kafkaTemplate, objectMapper, properties(),
				Clock.fixed(ATTEMPTED_AT, ZoneOffset.UTC));
	}

	@Test
	void acknowledgedMessageMarksOwnedClaimPublished() throws Exception {
		ClaimedFileOutboxEvent event = event(0);
		when(claimService.claimReady(ATTEMPTED_AT)).thenReturn(List.of(event));
		when(objectMapper.writeValueAsString(event.payload())).thenReturn("{}");
		when(kafkaTemplate.send(TOPIC, event.medicalFileId().toString(), "{}"))
				.thenReturn(CompletableFuture.completedFuture(null));

		assertEquals(1, publisher.publishReady());

		verify(claimService).markPublished(
				event.eventId(), event.claimToken(), ATTEMPTED_AT);
	}

	@Test
	void failedAcknowledgementReleasesClaimWithBoundedBackoff()
			throws Exception {
		ClaimedFileOutboxEvent event = event(0);
		when(claimService.claimReady(ATTEMPTED_AT)).thenReturn(List.of(event));
		when(objectMapper.writeValueAsString(event.payload())).thenReturn("{}");
		when(kafkaTemplate.send(TOPIC, event.medicalFileId().toString(), "{}"))
				.thenReturn(CompletableFuture.failedFuture(
						new IllegalStateException("synthetic outage")));

		assertEquals(1, publisher.publishReady());

		verify(claimService).markFailed(
				event.eventId(), event.claimToken(), ATTEMPTED_AT,
				Duration.ofSeconds(5), "IllegalStateException");
	}

	@Test
	void emptyClaimBatchDoesNotTouchKafka() {
		when(claimService.claimReady(ATTEMPTED_AT)).thenReturn(List.of());

		assertEquals(0, publisher.publishReady());

		verifyNoInteractions(kafkaTemplate, objectMapper);
	}

	private static ClaimedFileOutboxEvent event(int attempts) {
		return new ClaimedFileOutboxEvent(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), attempts,
				Map.of("eventId", "synthetic"));
	}

	private static FileOutboxProperties properties() {
		return new FileOutboxProperties(
				true, TOPIC, 10, Duration.ofSeconds(1), Duration.ofSeconds(5),
				Duration.ofMinutes(1), Duration.ofSeconds(5),
				Duration.ofMinutes(5));
	}
}
