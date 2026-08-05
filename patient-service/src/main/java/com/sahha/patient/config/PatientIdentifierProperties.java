package com.sahha.patient.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sahha.patient.identifier")
public record PatientIdentifierProperties(String hmacSecret) {

	public PatientIdentifierProperties {
		if (hmacSecret == null
				|| hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException(
					"patient identifier HMAC secret must contain at least 32 bytes");
		}
	}
}
