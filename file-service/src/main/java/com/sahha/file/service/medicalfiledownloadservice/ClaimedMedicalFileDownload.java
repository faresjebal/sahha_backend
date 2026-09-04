package com.sahha.file.service.medicalfiledownloadservice;

import java.util.UUID;

record ClaimedMedicalFileDownload(
		UUID fileId,
		UUID organisationId,
		UUID actorUserId,
		String storageKey,
		String originalFilename,
		String contentType,
		long size) {
}
