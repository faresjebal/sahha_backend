package com.sahha.communication.client.scheduling;

import java.util.UUID;

public record PatientAccessContextResource(
		UUID appointmentId, UUID organisationId, UUID patientRegistrationId,
		UUID patientId, UUID doctorUserId, UUID doctorMembershipId,
		String status, long appointmentVersion) {
}
