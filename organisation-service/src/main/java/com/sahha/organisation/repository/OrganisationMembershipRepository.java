package com.sahha.organisation.repository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipStatus;
import com.sahha.organisation.entity.OrganisationRole;

public interface OrganisationMembershipRepository
		extends JpaRepository<OrganisationMembership, UUID> {

	boolean existsByOrganisationIdAndUserId(UUID organisationId, UUID userId);

	Optional<OrganisationMembership> findByOrganisationIdAndUserId(
			UUID organisationId,
			UUID userId);

	List<OrganisationMembership> findAllByUserIdAndStatus(
			UUID userId,
			OrganisationMembershipStatus status);

	Optional<OrganisationMembership> findByIdAndOrganisationId(
			UUID membershipId,
			UUID organisationId);

	@Query(
			value = """
					select distinct membership
					from OrganisationMembership membership,
						 OrganisationMembershipRole assignment
					where assignment.membershipId = membership.id
					  and membership.organisationId = :organisationId
					  and assignment.role = :role
					  and assignment.active = true
					""",
			countQuery = """
					select count(distinct membership.id)
					from OrganisationMembership membership,
						 OrganisationMembershipRole assignment
					where assignment.membershipId = membership.id
					  and membership.organisationId = :organisationId
					  and assignment.role = :role
					  and assignment.active = true
					""")
	Page<OrganisationMembership> findAllByOrganisationIdAndActiveRole(
			@Param("organisationId") UUID organisationId,
			@Param("role") OrganisationRole role,
			Pageable pageable);

	@Query(
			value = """
					select distinct membership
					from OrganisationMembership membership,
						 OrganisationMembershipRole assignment
					where assignment.membershipId = membership.id
					  and membership.organisationId = :organisationId
					  and assignment.role in :roles
					""",
			countQuery = """
					select count(distinct membership.id)
					from OrganisationMembership membership,
						 OrganisationMembershipRole assignment
					where assignment.membershipId = membership.id
					  and membership.organisationId = :organisationId
					  and assignment.role in :roles
					""")
	Page<OrganisationMembership> findAllStaffByOrganisationId(
			@Param("organisationId") UUID organisationId,
			@Param("roles") Collection<OrganisationRole> roles,
			Pageable pageable);
}
