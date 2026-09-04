package com.sahha.file.service.medicalfiledownloadservice;

import java.io.InputStream;
import java.util.UUID;

public record AuthorizedMedicalFileDownload(
		UUID fileId,
		String originalFilename,
		String contentType,
		long size,
		InputStream content) {
}
