package com.sahha.file.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MedicalFileResource(
		UUID fileId,
		UUID consultationId,
		String originalFilename,
		String contentType,
		long size,
		String uploadStatus,
		String scanStatus,
		boolean downloadAvailable,
		Instant createdAt,
		Instant uploadedAt,
		Instant availableAt,
		Instant rejectedAt) {
}
