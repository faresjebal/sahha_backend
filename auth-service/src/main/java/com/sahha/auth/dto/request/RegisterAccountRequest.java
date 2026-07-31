package com.sahha.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
		name = "RegisterAccountRequest",
		description = "Synthetic patient account details used for registration.")
public record RegisterAccountRequest(
		@NotBlank(message = "email is required")
		@Email(message = "email must be valid")
		@Size(max = 320, message = "email must not exceed 320 characters")
		@Schema(example = "synthetic.patient@example.com")
		String email,

		@NotBlank(message = "password is required")
		@Size(
				min = 12,
				max = 128,
				message = "password must contain between 12 and 128 characters")
		@Schema(
				format = "password",
				accessMode = Schema.AccessMode.WRITE_ONLY,
				example = "Synthetic passphrase 2026!")
		String password,

		@NotBlank(message = "firstName is required")
		@Size(max = 100, message = "firstName must not exceed 100 characters")
		@Schema(example = "Amal")
		String firstName,

		@NotBlank(message = "lastName is required")
		@Size(max = 100, message = "lastName must not exceed 100 characters")
		@Schema(example = "Mansour")
		String lastName,

		@Pattern(
				regexp = "^\\+[1-9][0-9]{7,14}$",
				message = "phoneNumber must use E.164 format")
		@Schema(
				description = "Optional phone number in E.164 format.",
				example = "+21620123456")
		String phoneNumber) {

	@Override
	public String toString() {
		return "RegisterAccountRequest[redacted]";
	}
}
