package com.sahha.organisation.dto.response;

import java.util.UUID;

public record SchedulingDoctorResponse(
		UUID membershipId,
		UUID organisationId,
		UUID userId,
		String displayName,
		long membershipVersion) {
}
