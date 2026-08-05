package com.sahha.organisation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.organisation.entity.DoctorProfile;

public interface DoctorProfileRepository
		extends JpaRepository<DoctorProfile, UUID> {

	Optional<DoctorProfile> findByMembershipIdAndOrganisationId(
			UUID membershipId,
			UUID organisationId);

	List<DoctorProfile> findAllByMembershipIdIn(
			Collection<UUID> membershipIds);
}
