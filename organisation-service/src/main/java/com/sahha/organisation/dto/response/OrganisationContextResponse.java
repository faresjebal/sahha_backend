package com.sahha.organisation.dto.response;

import java.util.Set;
import java.util.UUID;

import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.OrganisationType;

public record OrganisationContextResponse(
		UUID membershipId,
		UUID organisationId,
		String organisationName,
		OrganisationType organisationType,
		Set<OrganisationRole> roles,
		long membershipVersion) {
}
