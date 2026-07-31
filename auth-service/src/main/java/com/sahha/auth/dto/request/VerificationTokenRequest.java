package com.sahha.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A raw single-use verification token received by email.")
public record VerificationTokenRequest(
		@NotBlank(message = "token is required")
		@Size(max = 512, message = "token must not exceed 512 characters")
		@Schema(
				format = "password",
				accessMode = Schema.AccessMode.WRITE_ONLY,
				example = "paste-token-from-the-verification-link")
		String token) {

	@Override
	public String toString() {
		return "VerificationTokenRequest[redacted]";
	}
}
