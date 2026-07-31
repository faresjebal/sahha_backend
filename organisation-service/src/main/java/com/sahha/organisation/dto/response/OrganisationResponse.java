package com.sahha.organisation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.sahha.organisation.entity.OrganisationStatus;
import com.sahha.organisation.entity.OrganisationType;

public record OrganisationResponse(
		UUID id,
		String name,
		String legalName,
		OrganisationType type,
		OrganisationStatus status,
		String contactEmail,
		String phoneNumber,
		String address,
		String city,
		String region,
		String postalCode,
		String countryCode,
		String timeZone,
		UUID createdBy,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
