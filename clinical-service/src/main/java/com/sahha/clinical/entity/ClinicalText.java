package com.sahha.clinical.entity;

import java.util.Objects;

final class ClinicalText {

	private ClinicalText() {
	}

	static String required(String value, int maximumLength, String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		String stripped = value.strip();
		if (stripped.isEmpty() || stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}

	static String optional(String value, int maximumLength, String fieldName) {
		if (value == null || value.isBlank()) return null;
		String stripped = value.strip();
		if (stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}
}
