package com.sahha.scheduling.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.scheduling.entity.AppointmentAuditEvent;

public interface AppointmentAuditEventRepository
		extends JpaRepository<AppointmentAuditEvent, UUID> {

	long countByAppointmentId(UUID appointmentId);

	Optional<AppointmentAuditEvent> findByOrganisationIdAndCommandRequestId(
			UUID organisationId,
			UUID commandRequestId);
}
