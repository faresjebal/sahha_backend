package com.sahha.patient.service.patientidentityservice;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class PatientDataNormalizer {

	public String name(String value) {
		return normalizedText(value).toLowerCase(Locale.ROOT);
	}

	public String email(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return Normalizer.normalize(value, Normalizer.Form.NFKC)
				.strip()
				.toLowerCase(Locale.ROOT);
	}

	public String phone(String value) {
		if (value == null) {
			throw new IllegalArgumentException("phoneNumber must not be null");
		}
		String source = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
		boolean international = source.startsWith("+");
		String digits = source.replaceAll("[^0-9]", "");
		if (digits.length() < 7 || digits.length() > 15) {
			throw new IllegalArgumentException("phoneNumber is invalid");
		}
		return international ? "+" + digits : digits;
	}

	public String search(String value) {
		return value == null || value.isBlank()
				? ""
				: normalizedText(value).toLowerCase(Locale.ROOT);
	}

	private static String normalizedText(String value) {
		if (value == null) {
			throw new IllegalArgumentException("value must not be null");
		}
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
				.strip()
				.replaceAll("\\s+", " ");
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("value must not be blank");
		}
		return normalized;
	}
}
