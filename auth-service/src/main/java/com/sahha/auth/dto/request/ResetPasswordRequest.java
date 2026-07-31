package com.sahha.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A reset token and replacement password.")
public record ResetPasswordRequest(
		@NotBlank(message = "token is required")
		@Size(max = 512, message = "token must not exceed 512 characters")
		@Schema(
				format = "password",
				accessMode = Schema.AccessMode.WRITE_ONLY,
				example = "paste-token-from-the-password-reset-link")
		String token,

		@NotBlank(message = "newPassword is required")
		@Size(
				min = 12,
				max = 128,
				message = "newPassword must contain between 12 and 128 characters")
		@Schema(
				format = "password",
				accessMode = Schema.AccessMode.WRITE_ONLY,
				example = "Replacement passphrase 2026!")
		String newPassword) {

	@Override
	public String toString() {
		return "ResetPasswordRequest[redacted]";
	}
}
