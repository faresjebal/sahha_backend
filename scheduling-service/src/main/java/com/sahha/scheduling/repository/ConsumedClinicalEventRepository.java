package com.sahha.scheduling.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.scheduling.entity.ConsumedClinicalEvent;

public interface ConsumedClinicalEventRepository
		extends JpaRepository<ConsumedClinicalEvent, UUID> {

	long countByAppointmentId(UUID appointmentId);
}
