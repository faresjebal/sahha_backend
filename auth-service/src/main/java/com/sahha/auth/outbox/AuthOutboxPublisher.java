package com.sahha.auth.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.config.AuthOutboxProperties;
import com.sahha.auth.entity.AuthOutboxEvent;
import com.sahha.auth.repository.AuthOutboxEventRepository;

import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
		prefix = "sahha.auth.outbox",
		name = "publisher-enabled",
		havingValue = "true")
public class AuthOutboxPublisher {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(AuthOutboxPublisher.class);

	private final AuthOutboxEventRepository outboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final AuthOutboxProperties properties;
	private final Clock clock;

	public AuthOutboxPublisher(
			AuthOutboxEventRepository outboxRepository,
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			AuthOutboxProperties properties,
			Clock clock) {
		this.outboxRepository = outboxRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${sahha.auth.outbox.fixed-delay:PT1S}")
	@Transactional
	public int publishReady() {
		Instant attemptedAt = clock.instant();
		List<AuthOutboxEvent> events =
				outboxRepository.findReadyForPublication(
						attemptedAt,
						PageRequest.of(0, properties.batchSize()));
		for (AuthOutboxEvent event : events) {
			publish(event, attemptedAt);
		}
		return events.size();
	}

	private void publish(AuthOutboxEvent event, Instant attemptedAt) {
		try {
			String payload = objectMapper.writeValueAsString(
					event.getPayload());
			kafkaTemplate.send(
						properties.topic(),
						event.getSecurityEventId().toString(),
						payload)
					.get(
							properties.sendTimeout().toMillis(),
							TimeUnit.MILLISECONDS);
			event.markPublished(attemptedAt);
		}
		catch (Exception failure) {
			String errorCode = failure.getClass().getSimpleName();
			event.markFailed(
					attemptedAt,
					retryDelay(event.getPublicationAttempts()),
					errorCode);
			LOGGER.warn(
					"Auth outbox publication failed eventId={} error={}",
					event.getId(),
					errorCode);
			if (failure instanceof InterruptedException) {
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
}
