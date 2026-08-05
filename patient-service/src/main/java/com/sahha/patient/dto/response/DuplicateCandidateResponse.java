package com.sahha.patient.dto.response;

import java.util.List;
import java.util.UUID;

import com.sahha.patient.entity.PatientSex;

public record DuplicateCandidateResponse(
		UUID patientId,
		String maskedDisplayName,
		int birthYear,
		PatientSex sex,
		String maskedIdentifier,
		int score,
		boolean exactStrongIdentifierMatch,
		List<DuplicateMatchReason> matchReasons,
		boolean registeredInActiveOrganisation,
		UUID activeOrganisationRegistrationId) {
}
