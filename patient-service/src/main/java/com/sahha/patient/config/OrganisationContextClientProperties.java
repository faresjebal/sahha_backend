package com.sahha.patient.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sahha.patient.organisation-client")
public record OrganisationContextClientProperties(URI baseUrl) {

	public OrganisationContextClientProperties {
		if (baseUrl == null || baseUrl.getScheme() == null) {
			throw new IllegalArgumentException(
					"organisation client base URL must be absolute");
		}
	}
}
