package com.sahha.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

class SmtpConfigurationValidatorTests {

	private final AuthMailProperties properties = new AuthMailProperties(
			true,
			"Sahha",
			"sender@example.com",
			URI.create("http://localhost:5173"),
			"/verify-email",
			"/reset-password");

	@Test
	void acceptsCompleteSmtpConfiguration() {
		assertDoesNotThrow(() -> new SmtpConfigurationValidator(
				properties,
				"brevo-smtp-login",
				"synthetic-smtp-key"));
	}

	@Test
	void rejectsMissingSmtpCredentialsWhenMailIsEnabled() {
		assertThrows(
				IllegalStateException.class,
				() -> new SmtpConfigurationValidator(
						properties,
						"",
						"synthetic-smtp-key"));
		assertThrows(
				IllegalStateException.class,
				() -> new SmtpConfigurationValidator(
						properties,
						"brevo-smtp-login",
						""));
	}
}
