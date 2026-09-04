package com.sahha.file.service.medicalfileservice;

import java.util.UUID;

record ClaimedMedicalFileUpload(
		UUID fileId,
		UUID organisationId,
		UUID actorUserId,
		String storageKey,
		String contentType,
		long declaredSize,
		String expectedChecksumSha256) {
}
