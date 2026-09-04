package com.sahha.file.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.file.entity.FileOutboxEvent;

public interface FileOutboxEventRepository
		extends JpaRepository<FileOutboxEvent, UUID> {

	long countByMedicalFileId(UUID medicalFileId);

	@Query(value = """
			select event.*
			from file_outbox_event event
			where event.published_at is null
			  and event.next_attempt_at <= :observedAt
			  and (event.claim_until is null or event.claim_until <= :observedAt)
			order by event.occurred_at, event.id
			limit :batchSize
			for update skip locked
			""", nativeQuery = true)
	List<FileOutboxEvent> findClaimCandidates(
			@Param("observedAt") Instant observedAt,
			@Param("batchSize") int batchSize);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<FileOutboxEvent> findByIdAndClaimTokenAndPublishedAtIsNull(
			UUID id, UUID claimToken);
}
