package com.sahha.file.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record NegotiateMedicalFileUploadRequest(
		@NotNull UUID consultationId,
		@NotBlank @Size(max = 255) String originalFilename,
		@NotBlank @Size(max = 127) String contentType,
		@Positive long declaredSize,
		@Pattern(regexp = "(?i)^[0-9a-f]{64}$") String expectedChecksumSha256) {
}
