package com.sahha.communication.repository;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.sahha.communication.entity.CommunicationOutboxEvent;

public interface CommunicationOutboxEventRepository
		extends JpaRepository<CommunicationOutboxEvent, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<CommunicationOutboxEvent> findTop50ByPublishedAtIsNullOrderByOccurredAtAscIdAsc();
}
