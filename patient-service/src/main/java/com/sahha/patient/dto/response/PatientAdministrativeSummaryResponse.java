package com.sahha.patient.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.sahha.patient.entity.PatientRegistrationStatus;
import com.sahha.patient.entity.PatientSex;

public record PatientAdministrativeSummaryResponse(
		UUID registrationId,
		UUID patientId,
		String medicalRecordNumber,
		String firstName,
		String lastName,
		LocalDate dateOfBirth,
		PatientSex sex,
		String phoneNumber,
		String email,
		PatientRegistrationStatus registrationStatus,
		Instant registeredAt,
		long registrationVersion,
		long identityVersion) {
}
