package com.sahha.scheduling.client.patient;

import java.util.UUID;

public record PatientRegistrationResource(
		UUID registrationId,
		UUID patientId,
		UUID organisationId,
		String registrationStatus) {
}
