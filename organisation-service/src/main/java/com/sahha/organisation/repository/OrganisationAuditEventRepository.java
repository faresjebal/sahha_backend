package com.sahha.organisation.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.organisation.entity.OrganisationAuditEvent;

public interface OrganisationAuditEventRepository
		extends JpaRepository<OrganisationAuditEvent, UUID> {

	long countByOrganisationId(UUID organisationId);
}
