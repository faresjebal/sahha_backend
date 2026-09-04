package com.sahha.scheduling.outbox;

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

import com.sahha.scheduling.config.SchedulingOutboxProperties;

@ExtendWith(MockitoExtension.class)
class AppointmentOutboxPublisherTests {

	private static final Instant ATTEMPTED_AT =
			Instant.parse("2027-01-01T00:00:01Z");
	private static final String TOPIC = "sahha.scheduling.appointments.v1";

	@Mock
	private AppointmentOutboxClaimService claimService;

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@Mock
	private ObjectMapper objectMapper;

	private AppointmentOutboxPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new AppointmentOutboxPublisher(
				claimService,
				kafkaTemplate,
				objectMapper,
				properties(),
				Clock.fixed(ATTEMPTED_AT, ZoneOffset.UTC));
	}

	@Test
	void acknowledgedMessageMarksTheOwnedClaimPublished() throws Exception {
		ClaimedAppointmentOutboxEvent event = event(0);
		when(claimService.claimReady(ATTEMPTED_AT)).thenReturn(List.of(event));
		when(objectMapper.writeValueAsString(event.payload()))
				.thenReturn("{\"eventId\":\"synthetic\"}");
		when(kafkaTemplate.send(
				TOPIC,
				event.appointmentId().toString(),
				"{\"eventId\":\"synthetic\"}"))
				.thenReturn(CompletableFuture.completedFuture(null));
		when(claimService.markPublished(
				event.eventId(), event.claimToken(), ATTEMPTED_AT))
				.thenReturn(true);

		assertEquals(1, publisher.publishReady());

		verify(claimService).markPublished(
				event.eventId(), event.claimToken(), ATTEMPTED_AT);
	}

	@Test
	void failedAcknowledgementReleasesClaimWithBoundedBackoff()
			throws Exception {
		ClaimedAppointmentOutboxEvent event = event(0);
		when(claimService.claimReady(ATTEMPTED_AT)).thenReturn(List.of(event));
		when(objectMapper.writeValueAsString(event.payload())).thenReturn("{}");
		when(kafkaTemplate.send(
				TOPIC, event.appointmentId().toString(), "{}"))
				.thenReturn(CompletableFuture.failedFuture(
						new IllegalStateException("synthetic outage")));
		when(claimService.markFailed(
				event.eventId(),
				event.claimToken(),
				ATTEMPTED_AT,
				Duration.ofSeconds(5),
				"IllegalStateException"))
				.thenReturn(true);

		assertEquals(1, publisher.publishReady());

		verify(claimService).markFailed(
				event.eventId(),
				event.claimToken(),
				ATTEMPTED_AT,
				Duration.ofSeconds(5),
				"IllegalStateException");
	}

	@Test
	void emptyClaimBatchDoesNotTouchKafka() {
		when(claimService.claimReady(ATTEMPTED_AT)).thenReturn(List.of());

		assertEquals(0, publisher.publishReady());

		verifyNoInteractions(kafkaTemplate, objectMapper);
	}

	private static ClaimedAppointmentOutboxEvent event(int attempts) {
		return new ClaimedAppointmentOutboxEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				attempts,
				Map.of("eventId", "synthetic"));
	}

	private static SchedulingOutboxProperties properties() {
		return new SchedulingOutboxProperties(
				true,
				TOPIC,
				10,
				Duration.ofSeconds(1),
				Duration.ofSeconds(5),
				Duration.ofMinutes(1),
				Duration.ofSeconds(5),
				Duration.ofMinutes(5));
	}
}
