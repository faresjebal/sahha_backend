package com.sahha.file.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MedicalFileUploadResponse(
		UUID fileId,
		String uploadStatus,
		String scanStatus,
		long size,
		String checksumSha256,
		Instant uploadedAt) {
}
