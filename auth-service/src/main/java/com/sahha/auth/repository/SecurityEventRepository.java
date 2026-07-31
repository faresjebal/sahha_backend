package com.sahha.auth.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.auth.entity.SecurityEvent;

public interface SecurityEventRepository
		extends JpaRepository<SecurityEvent, UUID> {

	List<SecurityEvent> findAllByUserIdOrderByOccurredAtAsc(UUID userId);
}
