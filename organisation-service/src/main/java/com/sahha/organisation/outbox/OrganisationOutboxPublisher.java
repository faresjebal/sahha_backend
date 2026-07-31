package com.sahha.organisation.outbox;

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
import tools.jackson.databind.ObjectMapper;

import com.sahha.organisation.config.OrganisationOutboxProperties;
import com.sahha.organisation.entity.OrganisationOutboxEvent;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;

@Component
@ConditionalOnProperty(
		prefix = "sahha.organisation.outbox",
		name = "publisher-enabled",
		havingValue = "true")
public class OrganisationOutboxPublisher {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(OrganisationOutboxPublisher.class);

	private final OrganisationOutboxEventRepository outboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final OrganisationOutboxProperties properties;
	private final Clock clock;

	public OrganisationOutboxPublisher(
			OrganisationOutboxEventRepository outboxRepository,
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			OrganisationOutboxProperties properties,
			Clock clock) {
		this.outboxRepository = outboxRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(
			fixedDelayString =
					"${sahha.organisation.outbox.fixed-delay:PT1S}")
	@Transactional
	public int publishReady() {
		Instant attemptedAt = clock.instant();
		List<OrganisationOutboxEvent> events =
				outboxRepository.findReadyForPublication(
						attemptedAt,
						PageRequest.of(0, properties.batchSize()));
		for (OrganisationOutboxEvent event : events) {
			publish(event, attemptedAt);
		}
		return events.size();
	}

	private void publish(
			OrganisationOutboxEvent event,
			Instant attemptedAt) {
		try {
			String payload = objectMapper.writeValueAsString(
					event.getPayload());
			kafkaTemplate.send(
						properties.topic(),
						event.getOrganisationId().toString(),
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
					"Organisation outbox publication failed eventId={} error={}",
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
