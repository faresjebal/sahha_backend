package com.sahha.communication.outbox;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import com.sahha.communication.config.CommunicationOutboxProperties;
import com.sahha.communication.entity.CommunicationOutboxEvent;
import com.sahha.communication.repository.CommunicationOutboxEventRepository;

@Component
@ConditionalOnProperty(prefix = "sahha.communication.outbox",
		name = "publisher-enabled", havingValue = "true")
public class CommunicationOutboxPublisher {
	private static final Logger LOGGER = LoggerFactory.getLogger(CommunicationOutboxPublisher.class);
	private final CommunicationOutboxEventRepository repository;
	private final KafkaTemplate<String,String> kafka;
	private final ObjectMapper objectMapper;
	private final CommunicationOutboxProperties properties;
	private final Clock clock;

	public CommunicationOutboxPublisher(CommunicationOutboxEventRepository repository,
			KafkaTemplate<String,String> kafka, ObjectMapper objectMapper,
			CommunicationOutboxProperties properties, Clock clock) {
		this.repository = repository; this.kafka = kafka; this.objectMapper = objectMapper;
		this.properties = properties; this.clock = clock;
	}

	@Transactional
	@Scheduled(fixedDelayString = "${sahha.communication.outbox.fixed-delay:PT1S}")
	public int publishReady() {
		List<CommunicationOutboxEvent> ready = repository
				.findTop50ByPublishedAtIsNullOrderByOccurredAtAscIdAsc();
		if (ready.size() > properties.batchSize()) ready = ready.subList(0, properties.batchSize());
		for (CommunicationOutboxEvent event : ready) {
			try {
				String payload = objectMapper.writeValueAsString(event.getPayload());
				kafka.send(event.getDestinationTopic(), event.getAggregateId().toString(), payload)
						.get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
				event.published(clock.instant());
			}
			catch (Exception failure) {
				if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
				event.failed(clock.instant(), failure.getClass().getSimpleName());
				LOGGER.warn("Communication outbox publication failed eventId={} error={}",
						event.getId(), failure.getClass().getSimpleName());
			}
		}
		return ready.size();
	}
}
