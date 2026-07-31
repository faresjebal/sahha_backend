package com.sahha.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.auth.entity.AuthOutboxEvent;

public interface AuthOutboxEventRepository
		extends JpaRepository<AuthOutboxEvent, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select event
			from AuthOutboxEvent event
			where event.publishedAt is null
			  and event.nextAttemptAt <= :observedAt
			order by event.occurredAt asc
			""")
	List<AuthOutboxEvent> findReadyForPublication(
			@Param("observedAt") Instant observedAt,
			Pageable pageable);

	Optional<AuthOutboxEvent> findBySecurityEventId(UUID securityEventId);
}
