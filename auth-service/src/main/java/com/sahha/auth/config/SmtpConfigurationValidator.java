package com.sahha.auth.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		prefix = "sahha.auth.mail",
		name = "enabled",
		havingValue = "true")
public class SmtpConfigurationValidator {

	public SmtpConfigurationValidator(
			AuthMailProperties mailProperties,
			@Value("${spring.mail.username:}") String smtpUsername,
			@Value("${spring.mail.password:}") String smtpPassword) {
		requireSecret(smtpUsername, "SMTP username");
		requireSecret(smtpPassword, "SMTP password");
		requireValidAddress(mailProperties.fromEmail());
	}

	private static void requireSecret(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(
					fieldName + " is required when Auth email is enabled");
		}
	}

	private static void requireValidAddress(String address) {
		try {
			new InternetAddress(address, true);
		}
		catch (AddressException exception) {
			throw new IllegalStateException(
					"Auth mail sender address must be valid");
		}
	}
}
