package com.sahha.file.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MedicalFileDownloadGrantResponse(
		UUID grantId,
		UUID fileId,
		String downloadPath,
		String downloadToken,
		Instant expiresAt) {
}
