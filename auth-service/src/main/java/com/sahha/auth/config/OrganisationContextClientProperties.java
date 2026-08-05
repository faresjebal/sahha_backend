package com.sahha.auth.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sahha.auth.organisation-client")
public record OrganisationContextClientProperties(URI baseUrl) {

	public OrganisationContextClientProperties {
		if (baseUrl == null || baseUrl.getScheme() == null) {
			throw new IllegalArgumentException(
					"organisation client base URL must be absolute");
		}
	}
}
