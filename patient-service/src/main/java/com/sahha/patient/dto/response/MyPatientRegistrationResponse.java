package com.sahha.patient.dto.response;

import java.util.UUID;

import com.sahha.patient.entity.PatientRegistrationStatus;

public record MyPatientRegistrationResponse(
		UUID registrationId,
		UUID organisationId,
		String medicalRecordNumber,
		PatientRegistrationStatus status,
		String firstName,
		String lastName) {
}
