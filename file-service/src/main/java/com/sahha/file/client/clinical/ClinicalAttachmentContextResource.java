package com.sahha.file.client.clinical;

import java.util.UUID;

public record ClinicalAttachmentContextResource(
		UUID consultationId,
		UUID organisationId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID doctorUserId,
		String consultationStatus,
		long consultationVersion) {
}
