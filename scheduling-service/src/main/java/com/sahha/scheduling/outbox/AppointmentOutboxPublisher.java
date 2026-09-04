package com.sahha.scheduling.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import com.sahha.scheduling.config.SchedulingOutboxProperties;

@Component
@ConditionalOnProperty(
		prefix = "sahha.scheduling.outbox",
		name = "publisher-enabled",
		havingValue = "true")
public class AppointmentOutboxPublisher {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(AppointmentOutboxPublisher.class);

	private final AppointmentOutboxClaimService claimService;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final SchedulingOutboxProperties properties;
	private final Clock clock;

	public AppointmentOutboxPublisher(
			AppointmentOutboxClaimService claimService,
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			SchedulingOutboxProperties properties,
			Clock clock) {
		this.claimService = claimService;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString =
			"${sahha.scheduling.outbox.fixed-delay:PT1S}")
	public int publishReady() {
		List<ClaimedAppointmentOutboxEvent> events = claimService.claimReady(
				clock.instant());
		for (ClaimedAppointmentOutboxEvent event : events) {
			publish(event);
		}
		return events.size();
	}

	private void publish(ClaimedAppointmentOutboxEvent event) {
		try {
			String payload = objectMapper.writeValueAsString(event.payload());
			kafkaTemplate.send(
					properties.topic(),
					event.appointmentId().toString(),
					payload)
					.get(
							properties.sendTimeout().toMillis(),
							TimeUnit.MILLISECONDS);
			if (!claimService.markPublished(
					event.eventId(), event.claimToken(), clock.instant())) {
				LOGGER.warn(
						"Appointment outbox claim expired after publication eventId={}",
						event.eventId());
			}
		}
		catch (Exception failure) {
			Throwable root = rootCause(failure);
			String errorCode = root.getClass().getSimpleName();
			if (!claimService.markFailed(
					event.eventId(),
					event.claimToken(),
					clock.instant(),
					retryDelay(event.previousPublicationAttempts()),
					errorCode)) {
				LOGGER.warn(
						"Appointment outbox claim expired after failure eventId={} error={}",
						event.eventId(),
						errorCode);
			}
			else {
				LOGGER.warn(
						"Appointment outbox publication failed eventId={} error={}",
						event.eventId(),
						errorCode);
			}
			if (root instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private Duration retryDelay(int previousAttempts) {
		int exponent = Math.min(previousAttempts, 10);
		long multiplier = 1L << exponent;
		Duration proposed;
		try {
			proposed = properties.initialRetryDelay().multipliedBy(multiplier);
		}
		catch (ArithmeticException overflow) {
			proposed = properties.maximumRetryDelay();
		}
		return proposed.compareTo(properties.maximumRetryDelay()) > 0
				? properties.maximumRetryDelay()
				: proposed;
	}

	private static Throwable rootCause(Exception failure) {
		if (failure instanceof ExecutionException && failure.getCause() != null) {
			return failure.getCause();
		}
		return failure;
	}
}
