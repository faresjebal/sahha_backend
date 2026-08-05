package com.sahha.organisation.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DoctorProfileResponse(
		UUID id,
		UUID organisationId,
		UUID membershipId,
		String specialty,
		String professionalTitle,
		String licenceNumber,
		String registrationAuthority,
		String biography,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
