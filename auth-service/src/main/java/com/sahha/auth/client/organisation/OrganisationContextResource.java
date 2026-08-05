package com.sahha.auth.client.organisation;

import java.util.Set;
import java.util.UUID;

public record OrganisationContextResource(
		UUID membershipId,
		UUID organisationId,
		String organisationName,
		String organisationType,
		Set<String> roles,
		long membershipVersion) {
}
