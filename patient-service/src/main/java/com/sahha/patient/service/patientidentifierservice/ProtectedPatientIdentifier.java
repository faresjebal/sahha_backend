package com.sahha.patient.service.patientidentifierservice;

import com.sahha.patient.entity.PatientIdentifierType;

public record ProtectedPatientIdentifier(
		PatientIdentifierType type,
		String fingerprint,
		String lastFour,
		String countryCode) {

	public String maskedValue() {
		return "****" + lastFour;
	}
}
