package com.sahha.notification.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.notification.entity.RejectedCommunicationEvent;

public interface RejectedCommunicationEventRepository
		extends JpaRepository<RejectedCommunicationEvent, UUID> {
	boolean existsBySourceTopicAndSourcePartitionAndSourceOffset(
			String sourceTopic, int sourcePartition, long sourceOffset);
}
