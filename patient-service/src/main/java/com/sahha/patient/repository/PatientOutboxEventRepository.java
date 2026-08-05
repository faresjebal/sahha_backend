package com.sahha.patient.repository;

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

import com.sahha.patient.entity.PatientOutboxEvent;

public interface PatientOutboxEventRepository
		extends JpaRepository<PatientOutboxEvent, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select event
			from PatientOutboxEvent event
			where event.publishedAt is null
			  and event.nextAttemptAt <= :observedAt
			order by event.occurredAt asc
			""")
	List<PatientOutboxEvent> findReadyForPublication(
			@Param("observedAt") Instant observedAt,
			Pageable pageable);

	Optional<PatientOutboxEvent> findByAuditEventId(UUID auditEventId);
}
