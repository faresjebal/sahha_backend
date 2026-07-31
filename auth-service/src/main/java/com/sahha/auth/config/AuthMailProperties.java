package com.sahha.auth.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.auth.mail")
public record AuthMailProperties(
		@DefaultValue("false") boolean enabled,
		@DefaultValue("Sahha") String fromName,
		@DefaultValue("no-reply@example.invalid") String fromEmail,
		@DefaultValue("http://localhost:5173") URI frontendBaseUrl,
		@DefaultValue("/verify-email") String emailVerificationPath,
		@DefaultValue("/reset-password") String passwordResetPath) {

	public AuthMailProperties {
		fromName = requireText(fromName, "fromName");
		fromEmail = requireText(fromEmail, "fromEmail");
		frontendBaseUrl = requireHttpUri(frontendBaseUrl);
		emailVerificationPath = requireAbsolutePath(
				emailVerificationPath,
				"emailVerificationPath");
		passwordResetPath = requireAbsolutePath(
				passwordResetPath,
				"passwordResetPath");
	}

	private static URI requireHttpUri(URI value) {
		if (value == null
				|| value.getScheme() == null
				|| (!value.getScheme().equalsIgnoreCase("http")
					&& !value.getScheme().equalsIgnoreCase("https"))
				|| value.getHost() == null) {
			throw new IllegalArgumentException(
					"frontendBaseUrl must be an absolute HTTP or HTTPS URI");
		}
		return value;
	}

	private static String requireAbsolutePath(String value, String fieldName) {
		String required = requireText(value, fieldName);
		if (!required.startsWith("/")) {
			throw new IllegalArgumentException(fieldName + " must start with '/'");
		}
		return required;
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.strip();
	}
}
