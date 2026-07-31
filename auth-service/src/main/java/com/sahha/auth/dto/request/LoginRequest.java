package com.sahha.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
		name = "LoginRequest",
		description = "Credentials and an optional display name for this device.")
public record LoginRequest(
		@NotBlank(message = "email is required")
		@Email(message = "email must be valid")
		@Size(max = 320, message = "email must not exceed 320 characters")
		@Schema(example = "synthetic.patient@example.com")
		String email,

		@NotBlank(message = "password is required")
		@Size(max = 128, message = "password must not exceed 128 characters")
		@Schema(
				format = "password",
				accessMode = Schema.AccessMode.WRITE_ONLY,
				example = "Synthetic passphrase 2026!")
		String password,

		@Size(max = 120, message = "deviceName must not exceed 120 characters")
		@Schema(
				description = "Optional user-facing device label.",
				example = "Fares laptop")
		String deviceName) {

	@Override
	public String toString() {
		return "LoginRequest[redacted]";
	}
}
