package com.sahha.clinical.repository;

import java.util.UUID;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.clinical.entity.ClinicalOutboxEvent;

public interface ClinicalOutboxEventRepository
		extends JpaRepository<ClinicalOutboxEvent, UUID> {

	long countByAggregateId(UUID aggregateId);

	Optional<ClinicalOutboxEvent>
			findFirstByAggregateIdAndEventTypeOrderByOccurredAtDesc(
					UUID aggregateId, String eventType);

	@Query(value = """
			select event.*
			from clinical_outbox_event event
			where event.published_at is null
			  and event.next_attempt_at <= :observedAt
			  and (event.claim_until is null or event.claim_until <= :observedAt)
			order by event.occurred_at, event.id
			limit :batchSize
			for update skip locked
			""", nativeQuery = true)
	List<ClinicalOutboxEvent> findClaimCandidates(
			@Param("observedAt") Instant observedAt,
			@Param("batchSize") int batchSize);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<ClinicalOutboxEvent> findByIdAndClaimTokenAndPublishedAtIsNull(
			UUID id, UUID claimToken);
}
