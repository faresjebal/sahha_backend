package com.sahha.file.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.file.config.FileOutboxProperties;
import com.sahha.file.entity.FileOutboxEvent;
import com.sahha.file.repository.FileOutboxEventRepository;

@Service
public class FileOutboxClaimService {

	private final FileOutboxEventRepository repository;
	private final FileOutboxProperties properties;

	public FileOutboxClaimService(
			FileOutboxEventRepository repository,
			FileOutboxProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	@Transactional
	public List<ClaimedFileOutboxEvent> claimReady(Instant observedAt) {
		UUID claimToken = UUID.randomUUID();
		List<FileOutboxEvent> events = repository.findClaimCandidates(
				observedAt, properties.batchSize());
		for (FileOutboxEvent event : events) {
			event.claim(claimToken, observedAt, properties.claimLease());
		}
		repository.flush();
		return events.stream()
				.map(event -> new ClaimedFileOutboxEvent(
						event.getId(), claimToken, event.getMedicalFileId(),
						event.getPublicationAttempts(), Map.copyOf(event.getPayload())))
				.toList();
	}

	@Transactional
	public boolean markPublished(
			UUID eventId,
			UUID claimToken,
			Instant publishedAt) {
		FileOutboxEvent event = claimed(eventId, claimToken);
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
		FileOutboxEvent event = claimed(eventId, claimToken);
		if (event == null) return false;
		event.markFailed(claimToken, attemptedAt, retryDelay, errorCode);
		return true;
	}

	private FileOutboxEvent claimed(UUID eventId, UUID claimToken) {
		return repository.findByIdAndClaimTokenAndPublishedAtIsNull(
				eventId, claimToken).orElse(null);
	}
}
