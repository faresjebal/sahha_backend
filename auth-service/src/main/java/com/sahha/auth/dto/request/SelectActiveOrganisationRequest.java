package com.sahha.auth.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record SelectActiveOrganisationRequest(
		@NotNull(message = "organisationId is required")
		UUID organisationId) {
}
