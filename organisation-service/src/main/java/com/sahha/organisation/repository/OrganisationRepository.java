package com.sahha.organisation.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.organisation.entity.Organisation;

public interface OrganisationRepository
		extends JpaRepository<Organisation, UUID> {

	boolean existsByNormalizedName(String normalizedName);
}
