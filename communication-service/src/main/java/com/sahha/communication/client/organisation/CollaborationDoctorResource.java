package com.sahha.communication.client.organisation;

import java.util.UUID;

public record CollaborationDoctorResource(
		UUID membershipId, UUID organisationId, UUID userId,
		String displayName, long membershipVersion) {
}
