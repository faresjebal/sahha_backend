package com.sahha.clinical.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.clinical.entity.ClinicalAuditEvent;

public interface ClinicalAuditEventRepository
		extends JpaRepository<ClinicalAuditEvent, UUID> {

	long countByConsultationId(UUID consultationId);
}
