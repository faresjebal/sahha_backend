package com.sahha.patient.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sahha.patient.auth-client")
public record AuthAccountClientProperties(URI baseUrl) {

	public AuthAccountClientProperties {
		if (baseUrl == null || baseUrl.getScheme() == null) {
			throw new IllegalArgumentException("auth client base URL must be absolute");
		}
	}
}
