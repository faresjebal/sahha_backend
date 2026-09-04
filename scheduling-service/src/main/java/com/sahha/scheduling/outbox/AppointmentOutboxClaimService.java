package com.sahha.scheduling.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.scheduling.config.SchedulingOutboxProperties;
import com.sahha.scheduling.entity.AppointmentOutboxEvent;
import com.sahha.scheduling.repository.AppointmentOutboxEventRepository;

@Service
public class AppointmentOutboxClaimService {

	private final AppointmentOutboxEventRepository repository;
	private final SchedulingOutboxProperties properties;

	public AppointmentOutboxClaimService(
			AppointmentOutboxEventRepository repository,
			SchedulingOutboxProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	@Transactional
	public List<ClaimedAppointmentOutboxEvent> claimReady(Instant observedAt) {
		UUID claimToken = UUID.randomUUID();
		List<AppointmentOutboxEvent> events = repository.findClaimCandidates(
				observedAt, properties.batchSize());
		for (AppointmentOutboxEvent event : events) {
			event.claim(claimToken, observedAt, properties.claimLease());
		}
		repository.flush();
		return events.stream()
				.map(event -> new ClaimedAppointmentOutboxEvent(
						event.getId(),
						claimToken,
						event.getAppointmentId(),
						event.getPublicationAttempts(),
						Map.copyOf(event.getPayload())))
				.toList();
	}

	@Transactional
	public boolean markPublished(
			UUID eventId,
			UUID claimToken,
			Instant publishedAt) {
		AppointmentOutboxEvent event = claimed(eventId, claimToken);
		if (event == null) {
			return false;
		}
		event.markPublished(claimToken, publishedAt);
		return true;
	}

	@Transactional
	public boolean markFailed(
			UUID eventId,
			UUID claimToken,
			Instant attemptedAt,
			Duration retryDelay,
			String errorCode) {
		AppointmentOutboxEvent event = claimed(eventId, claimToken);
		if (event == null) {
			return false;
		}
		event.markFailed(
				claimToken, attemptedAt, retryDelay, errorCode);
		return true;
	}

	private AppointmentOutboxEvent claimed(UUID eventId, UUID claimToken) {
		return repository.findByIdAndClaimTokenAndPublishedAtIsNull(
				eventId, claimToken).orElse(null);
	}
}
