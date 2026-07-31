package com.sahha.organisation.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.organisation.entity.OrganisationOutboxEvent;

public interface OrganisationOutboxEventRepository
		extends JpaRepository<OrganisationOutboxEvent, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select event
			from OrganisationOutboxEvent event
			where event.publishedAt is null
			  and event.nextAttemptAt <= :observedAt
			order by event.occurredAt asc
			""")
	List<OrganisationOutboxEvent> findReadyForPublication(
			@Param("observedAt") Instant observedAt,
			Pageable pageable);

	Optional<OrganisationOutboxEvent> findByAuditEventId(UUID auditEventId);
}
