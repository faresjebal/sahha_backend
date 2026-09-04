package com.sahha.scheduling.client.organisation;

import java.util.UUID;

public record SchedulingDoctorResource(
		UUID membershipId,
		UUID organisationId,
		UUID userId,
		String displayName,
		long membershipVersion) {
}
