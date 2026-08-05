package com.sahha.patient.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.sahha.patient.config.PatientIdentifierProperties;
import com.sahha.patient.dto.request.PatientIdentifierRequest;
import com.sahha.patient.entity.PatientIdentifierType;
import com.sahha.patient.service.patientidentifierservice.PatientIdentifierProtectionService;

class PatientIdentifierProtectionServiceTests {

	private final PatientIdentifierProtectionService service =
			new PatientIdentifierProtectionService(
					new PatientIdentifierProperties(
							"synthetic-unit-test-patient-identifier-key-2026"));

	@Test
	void canonicalFormattingProducesTheSameNonReversibleFingerprint() {
		var first = service.protect(new PatientIdentifierRequest(
				PatientIdentifierType.NATIONAL_ID,
				"TN-0735-8821",
				"TN"));
		var second = service.protect(new PatientIdentifierRequest(
				PatientIdentifierType.NATIONAL_ID,
				"tn 0735 8821",
				"TN"));

		assertEquals(first.fingerprint(), second.fingerprint());
		assertEquals("8821", first.lastFour());
		assertEquals("****8821", first.maskedValue());
		assertFalse(first.fingerprint().contains("07358821"));
	}

	@Test
	void identifierTypeAndCountryArePartOfTheFingerprintScope() {
		var national = service.protect(new PatientIdentifierRequest(
				PatientIdentifierType.NATIONAL_ID,
				"ABCD1234",
				"TN"));
		var passport = service.protect(new PatientIdentifierRequest(
				PatientIdentifierType.PASSPORT,
				"ABCD1234",
				"TN"));
		var otherCountry = service.protect(new PatientIdentifierRequest(
				PatientIdentifierType.NATIONAL_ID,
				"ABCD1234",
				"FR"));

		assertNotEquals(national.fingerprint(), passport.fingerprint());
		assertNotEquals(national.fingerprint(), otherCountry.fingerprint());
	}
}
