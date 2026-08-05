package com.sahha.organisation.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sahha.organisation.entity.OrganisationMembershipStatus;
import com.sahha.organisation.entity.OrganisationRole;

public record StaffMemberResponse(
		UUID membershipId,
		UUID organisationId,
		UUID userId,
		String email,
		String displayName,
		OrganisationMembershipStatus status,
		Set<OrganisationRole> roles,
		List<StaffDepartmentAssignmentResponse> departmentAssignments,
		DoctorProfileResponse doctorProfile,
		Instant joinedAt,
		Instant updatedAt,
		long version) {
}
