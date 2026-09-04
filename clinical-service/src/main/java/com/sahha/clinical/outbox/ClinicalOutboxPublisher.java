package com.sahha.clinical.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import com.sahha.clinical.config.ClinicalOutboxProperties;

@Component
@ConditionalOnProperty(
		prefix = "sahha.clinical.outbox",
		name = "publisher-enabled",
		havingValue = "true")
public class ClinicalOutboxPublisher {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(ClinicalOutboxPublisher.class);
	private final ClinicalOutboxClaimService claimService;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final ClinicalOutboxProperties properties;
	private final Clock clock;

	public ClinicalOutboxPublisher(
			ClinicalOutboxClaimService claimService,
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			ClinicalOutboxProperties properties,
			Clock clock) {
		this.claimService = claimService;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${sahha.clinical.outbox.fixed-delay:PT1S}")
	public int publishReady() {
		List<ClaimedClinicalOutboxEvent> events = claimService.claimReady(clock.instant());
		for (ClaimedClinicalOutboxEvent event : events) {
			publish(event);
		}
		return events.size();
	}

	private void publish(ClaimedClinicalOutboxEvent event) {
		try {
			String payload = objectMapper.writeValueAsString(event.payload());
			kafkaTemplate.send(properties.topic(), event.consultationId().toString(), payload)
					.get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
			claimService.markPublished(
					event.eventId(), event.claimToken(), clock.instant());
		}
		catch (Exception failure) {
			Throwable root = rootCause(failure);
			String errorCode = root.getClass().getSimpleName();
			claimService.markFailed(
					event.eventId(), event.claimToken(), clock.instant(),
					retryDelay(event.previousPublicationAttempts()), errorCode);
			LOGGER.warn("Clinical outbox publication failed eventId={} error={}",
					event.eventId(), errorCode);
			if (root instanceof InterruptedException) Thread.currentThread().interrupt();
		}
	}

	private Duration retryDelay(int previousAttempts) {
		int exponent = Math.min(previousAttempts, 10);
		Duration proposed;
		try {
			proposed = properties.initialRetryDelay().multipliedBy(1L << exponent);
		}
		catch (ArithmeticException overflow) {
			proposed = properties.maximumRetryDelay();
		}
		return proposed.compareTo(properties.maximumRetryDelay()) > 0
				? properties.maximumRetryDelay() : proposed;
	}

	private static Throwable rootCause(Exception failure) {
		return failure instanceof ExecutionException && failure.getCause() != null
				? failure.getCause() : failure;
	}
}
