package com.sahha.notification.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.notification.entity.ConsumedCommunicationEvent;

public interface ConsumedCommunicationEventRepository
		extends JpaRepository<ConsumedCommunicationEvent, UUID> {
}
