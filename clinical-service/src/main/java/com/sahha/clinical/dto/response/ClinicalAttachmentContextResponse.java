package com.sahha.clinical.dto.response;

import java.util.UUID;

public record ClinicalAttachmentContextResponse(
		UUID consultationId,
		UUID organisationId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID doctorUserId,
		String consultationStatus,
		long consultationVersion) {
}
