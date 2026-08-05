package com.sahha.organisation.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.sahha.organisation.entity.OrganisationMembershipStatus;
import com.sahha.organisation.entity.OrganisationRole;

public record OrganisationMembershipResponse(
		UUID id,
		UUID organisationId,
		UUID userId,
		String email,
		String displayName,
		OrganisationMembershipStatus status,
		Set<OrganisationRole> roles,
		Instant joinedAt,
		UUID createdBy,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
