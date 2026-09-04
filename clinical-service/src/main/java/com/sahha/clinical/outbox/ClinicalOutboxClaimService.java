package com.sahha.clinical.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.clinical.config.ClinicalOutboxProperties;
import com.sahha.clinical.entity.ClinicalOutboxEvent;
import com.sahha.clinical.repository.ClinicalOutboxEventRepository;

@Service
public class ClinicalOutboxClaimService {

	private final ClinicalOutboxEventRepository repository;
	private final ClinicalOutboxProperties properties;

	public ClinicalOutboxClaimService(
			ClinicalOutboxEventRepository repository,
			ClinicalOutboxProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	@Transactional
	public List<ClaimedClinicalOutboxEvent> claimReady(Instant observedAt) {
		UUID claimToken = UUID.randomUUID();
		List<ClinicalOutboxEvent> events = repository.findClaimCandidates(
				observedAt, properties.batchSize());
		for (ClinicalOutboxEvent event : events) {
			event.claim(claimToken, observedAt, properties.claimLease());
		}
		repository.flush();
		return events.stream()
				.map(event -> new ClaimedClinicalOutboxEvent(
						event.getId(),
						claimToken,
						event.getAggregateId(),
						event.getPublicationAttempts(),
						Map.copyOf(event.getPayload())))
				.toList();
	}

	@Transactional
	public boolean markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
		ClinicalOutboxEvent event = claimed(eventId, claimToken);
		if (event == null) return false;
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
		ClinicalOutboxEvent event = claimed(eventId, claimToken);
		if (event == null) return false;
		event.markFailed(claimToken, attemptedAt, retryDelay, errorCode);
		return true;
	}

	private ClinicalOutboxEvent claimed(UUID eventId, UUID claimToken) {
		return repository.findByIdAndClaimTokenAndPublishedAtIsNull(
				eventId, claimToken).orElse(null);
	}
}
