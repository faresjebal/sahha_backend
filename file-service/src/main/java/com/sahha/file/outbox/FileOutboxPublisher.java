package com.sahha.file.outbox;

import java.time.Clock;
import java.time.Duration;
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

import com.sahha.file.config.FileOutboxProperties;

@Component
@ConditionalOnProperty(
		prefix = "sahha.file.outbox",
		name = "publisher-enabled",
		havingValue = "true")
public class FileOutboxPublisher {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(FileOutboxPublisher.class);
	private final FileOutboxClaimService claimService;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final FileOutboxProperties properties;
	private final Clock clock;

	public FileOutboxPublisher(
			FileOutboxClaimService claimService,
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			FileOutboxProperties properties,
			Clock clock) {
		this.claimService = claimService;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${sahha.file.outbox.fixed-delay:PT1S}")
	public int publishReady() {
		List<ClaimedFileOutboxEvent> events = claimService.claimReady(clock.instant());
		for (ClaimedFileOutboxEvent event : events) {
			publish(event);
		}
		return events.size();
	}

	private void publish(ClaimedFileOutboxEvent event) {
		try {
			String payload = objectMapper.writeValueAsString(event.payload());
			kafkaTemplate.send(
					properties.topic(), event.medicalFileId().toString(), payload)
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
			LOGGER.warn("File outbox publication failed eventId={} error={}",
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
