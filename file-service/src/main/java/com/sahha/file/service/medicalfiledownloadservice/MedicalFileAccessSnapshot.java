package com.sahha.file.service.medicalfiledownloadservice;

import java.util.UUID;

import com.sahha.file.client.clinical.ClinicalAttachmentContextResource;

record MedicalFileAccessSnapshot(
		UUID fileId,
		UUID organisationId,
		UUID consultationId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID uploaderUserId) {

	boolean matches(ClinicalAttachmentContextResource context) {
		return context != null
				&& consultationId.equals(context.consultationId())
				&& organisationId.equals(context.organisationId())
				&& patientRegistrationId.equals(context.patientRegistrationId())
				&& patientId.equals(context.patientId())
				&& uploaderUserId.equals(context.doctorUserId());
	}
}
