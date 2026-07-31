package com.sahha.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Email address for a public account-lifecycle request.")
public record EmailAddressRequest(
		@NotBlank(message = "email is required")
		@Email(message = "email must be valid")
		@Size(max = 320, message = "email must not exceed 320 characters")
		@Schema(example = "synthetic.patient@example.com")
		String email) {

	@Override
	public String toString() {
		return "EmailAddressRequest[redacted]";
	}
}
