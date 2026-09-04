package com.sahha.notification.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.notification.entity.RejectedAppointmentEvent;

public interface RejectedAppointmentEventRepository
		extends JpaRepository<RejectedAppointmentEvent, UUID> {

	boolean existsBySourceTopicAndSourcePartitionAndSourceOffset(
			String sourceTopic,
			int sourcePartition,
			long sourceOffset);
}
