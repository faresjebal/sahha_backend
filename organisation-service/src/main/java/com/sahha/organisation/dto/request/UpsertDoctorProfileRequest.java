package com.sahha.organisation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpsertDoctorProfileRequest(
		@NotBlank
		@Size(max = 120)
		String specialty,
		@NotBlank
		@Size(max = 120)
		String professionalTitle,
		@NotBlank
		@Size(max = 80)
		String licenceNumber,
		@NotBlank
		@Size(max = 160)
		String registrationAuthority,
		@Size(max = 1000)
		String biography,
		@PositiveOrZero
		Long version) {
}
