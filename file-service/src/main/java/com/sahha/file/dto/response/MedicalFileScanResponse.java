package com.sahha.file.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MedicalFileScanResponse(
		UUID fileId,
		String uploadStatus,
		String scanStatus,
		Instant availableAt,
		Instant rejectedAt,
		boolean alreadyApplied) {
}
