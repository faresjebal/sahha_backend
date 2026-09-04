package com.sahha.notification.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.notification.entity.ConsumedAppointmentEvent;

public interface ConsumedAppointmentEventRepository
		extends JpaRepository<ConsumedAppointmentEvent, UUID> {

	long countByAppointmentId(UUID appointmentId);
}
