package com.sahha.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.auth.jwt")
public record AuthJwtProperties(
		@DefaultValue("PT10M") Duration accessTokenLifetime,
		@DefaultValue("http://localhost:8081") String issuer,
		@DefaultValue("sahha-api") String audience,
		@DefaultValue("false") boolean ephemeralKeyEnabled,
		@DefaultValue("") String privateKeyBase64,
		@DefaultValue("") String publicKeyBase64) {

	public AuthJwtProperties {
		requirePositive(accessTokenLifetime, "accessTokenLifetime");
		if (accessTokenLifetime.compareTo(Duration.ofMinutes(15)) > 0) {
			throw new IllegalArgumentException(
					"accessTokenLifetime must not exceed 15 minutes");
		}
		issuer = requireText(issuer, "issuer");
		audience = requireText(audience, "audience");
		privateKeyBase64 = normalize(privateKeyBase64);
		publicKeyBase64 = normalize(publicKeyBase64);
		if ((privateKeyBase64 == null) != (publicKeyBase64 == null)) {
			throw new IllegalArgumentException(
					"private and public JWT keys must be configured together");
		}
		if (!ephemeralKeyEnabled && privateKeyBase64 == null) {
			throw new IllegalArgumentException(
					"JWT key material is required when ephemeral keys are disabled");
		}
	}

	public boolean hasConfiguredKeyPair() {
		return privateKeyBase64 != null;
	}

	private static void requirePositive(Duration value, String fieldName) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(fieldName + " must be positive");
		}
	}

	private static String requireText(String value, String fieldName) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return normalized;
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}
}
