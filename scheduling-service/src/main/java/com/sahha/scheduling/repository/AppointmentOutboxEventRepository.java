package com.sahha.scheduling.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.scheduling.entity.AppointmentOutboxEvent;

public interface AppointmentOutboxEventRepository
		extends JpaRepository<AppointmentOutboxEvent, UUID> {

	@Query(value = """
			select event.*
			from appointment_outbox_event event
			where event.published_at is null
			  and event.next_attempt_at <= :observedAt
			  and (
			      event.claim_until is null
			      or event.claim_until <= :observedAt
			  )
			order by event.occurred_at, event.id
			limit :batchSize
			for update skip locked
			""", nativeQuery = true)
	List<AppointmentOutboxEvent> findClaimCandidates(
			@Param("observedAt") Instant observedAt,
			@Param("batchSize") int batchSize);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AppointmentOutboxEvent> findByIdAndClaimTokenAndPublishedAtIsNull(
			UUID id,
			UUID claimToken);

	Optional<AppointmentOutboxEvent> findByAppointmentAuditEventId(
			UUID appointmentAuditEventId);

	long countByAppointmentId(UUID appointmentId);
}
