package com.sahha.clinical.client.scheduling;

import java.util.UUID;

public record ClinicalPatientAccessContextResource(
		UUID appointmentId,
		UUID organisationId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID doctorUserId,
		UUID doctorMembershipId,
		String status,
		long appointmentVersion) {
}
