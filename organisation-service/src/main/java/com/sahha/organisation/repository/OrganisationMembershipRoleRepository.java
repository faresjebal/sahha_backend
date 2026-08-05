package com.sahha.organisation.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.organisation.entity.OrganisationMembershipRole;

public interface OrganisationMembershipRoleRepository
		extends JpaRepository<OrganisationMembershipRole, UUID> {

	List<OrganisationMembershipRole> findAllByMembershipIdInAndActiveTrue(
			Collection<UUID> membershipIds);

	List<OrganisationMembershipRole> findAllByMembershipIdIn(
			Collection<UUID> membershipIds);

	List<OrganisationMembershipRole> findAllByMembershipId(
			UUID membershipId);

	List<OrganisationMembershipRole> findAllByMembershipIdAndActiveTrue(
			UUID membershipId);

	boolean existsByMembershipIdAndRoleAndActiveTrue(
			UUID membershipId,
			com.sahha.organisation.entity.OrganisationRole role);
}
