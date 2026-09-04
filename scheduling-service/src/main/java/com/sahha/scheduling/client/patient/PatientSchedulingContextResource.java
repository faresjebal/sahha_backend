package com.sahha.scheduling.client.patient;

import java.util.UUID;

public record PatientSchedulingContextResource(
		UUID registrationId,
		UUID patientId,
		UUID organisationId,
		String registrationStatus,
		UUID authUserId) {
}
