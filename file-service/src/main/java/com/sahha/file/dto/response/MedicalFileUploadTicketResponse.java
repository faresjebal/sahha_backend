package com.sahha.file.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MedicalFileUploadTicketResponse(
		UUID fileId,
		String uploadPath,
		String uploadToken,
		Instant expiresAt,
		String contentType,
		long declaredSize,
		String uploadStatus,
		String scanStatus) {
}
