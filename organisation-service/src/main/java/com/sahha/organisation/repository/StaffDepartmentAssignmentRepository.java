package com.sahha.organisation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.organisation.entity.StaffDepartmentAssignment;
import com.sahha.organisation.entity.StaffDepartmentAssignmentStatus;

public interface StaffDepartmentAssignmentRepository
		extends JpaRepository<StaffDepartmentAssignment, UUID> {

	List<StaffDepartmentAssignment> findAllByMembershipIdInOrderByCreatedAtDesc(
			Collection<UUID> membershipIds);

	List<StaffDepartmentAssignment> findAllByMembershipIdAndStatus(
			UUID membershipId,
			StaffDepartmentAssignmentStatus status);

	Optional<StaffDepartmentAssignment>
			findByIdAndMembershipIdAndOrganisationId(
					UUID assignmentId,
					UUID membershipId,
					UUID organisationId);
}
