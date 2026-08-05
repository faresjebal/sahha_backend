package com.sahha.patient.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.sahha.patient.entity.PatientIdentifierType;
import com.sahha.patient.entity.PatientRegistrationStatus;
import com.sahha.patient.entity.PatientSex;

public record PatientAdministrativeResponse(
		UUID registrationId,
		UUID patientId,
		UUID organisationId,
		String medicalRecordNumber,
		PatientRegistrationStatus registrationStatus,
		String firstName,
		String lastName,
		LocalDate dateOfBirth,
		PatientSex sex,
		PatientIdentifierType identifierType,
		String maskedIdentifier,
		String identifierCountryCode,
		String phoneNumber,
		String email,
		String address,
		String city,
		String region,
		String postalCode,
		String countryCode,
		String emergencyContactName,
		String emergencyContactPhone,
		String emergencyContactRelationship,
		String preferredLanguage,
		String accessibilityNeeds,
		Instant privacyNoticeAcknowledgedAt,
		Instant registeredAt,
		UUID registeredBy,
		UUID updatedBy,
		Instant createdAt,
		Instant updatedAt,
		long registrationVersion,
		long identityVersion) {
}
