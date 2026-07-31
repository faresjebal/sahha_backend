package com.sahha.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank
		@Size(max = 128)
		@Schema(
				format = "password",
				example = "Current synthetic passphrase 2026!")
		String currentPassword,
		@NotBlank
		@Size(max = 128)
		@Schema(
				format = "password",
				example = "Replacement synthetic passphrase 2026!")
		String newPassword) {
}
