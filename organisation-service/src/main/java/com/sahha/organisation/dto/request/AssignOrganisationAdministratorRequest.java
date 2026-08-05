package com.sahha.organisation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignOrganisationAdministratorRequest(
		@NotBlank(message = "email is required")
		@Email(message = "email must be valid")
		@Size(max = 254, message = "email must contain at most 254 characters")
		String email) {
}
