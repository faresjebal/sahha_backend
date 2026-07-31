package com.sahha.auth.service.useraccountservice;

import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class EmailNormalizer {

	public String normalize(String email) {
		Objects.requireNonNull(email, "email must not be null");
		String normalized = email.strip().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("email must not be blank");
		}
		return normalized;
	}
}
