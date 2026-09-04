package com.sahha.clinical.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.clinical.entity.ClinicalAccessAuditEvent;

public interface ClinicalAccessAuditEventRepository
		extends JpaRepository<ClinicalAccessAuditEvent, UUID> {

	long countByResourceTypeAndResourceId(String resourceType, UUID resourceId);
}
