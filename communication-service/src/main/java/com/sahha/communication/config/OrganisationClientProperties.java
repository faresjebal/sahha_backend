package com.sahha.communication.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sahha.communication.organisation-client")
public record OrganisationClientProperties(URI baseUrl) {
	public OrganisationClientProperties {
		if (baseUrl == null || baseUrl.getScheme() == null) {
			throw new IllegalArgumentException("Organisation client base URL is invalid");
		}
	}
}
