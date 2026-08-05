package com.sahha.patient.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.patient.entity.PatientAuditEvent;

public interface PatientAuditEventRepository
		extends JpaRepository<PatientAuditEvent, UUID> {

	List<PatientAuditEvent> findAllByOrganisationIdAndResourceIdOrderByOccurredAtDescIdDesc(
			UUID organisationId,
			UUID resourceId);

	long countByOrganisationId(UUID organisationId);
}
