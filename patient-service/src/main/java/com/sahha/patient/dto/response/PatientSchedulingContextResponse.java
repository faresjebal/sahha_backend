package com.sahha.patient.dto.response;

import java.util.UUID;

import com.sahha.patient.entity.PatientRegistrationStatus;

public record PatientSchedulingContextResponse(
		UUID registrationId,
		UUID patientId,
		UUID organisationId,
		PatientRegistrationStatus registrationStatus,
		UUID authUserId) {
}
