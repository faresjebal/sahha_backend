package com.sahha.organisation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.StaffInvitationStatus;

public record StaffInvitationResponse(
		UUID id,
		UUID organisationId,
		String organisationName,
		String email,
		OrganisationRole role,
		StaffInvitationStatus status,
		Instant expiresAt,
		Instant resolvedAt,
		UUID resolvedByUserId,
		UUID acceptedMembershipId,
		UUID createdBy,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
