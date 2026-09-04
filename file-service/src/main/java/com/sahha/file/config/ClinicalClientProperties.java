package com.sahha.file.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.file.clinical-client")
public record ClinicalClientProperties(
		@DefaultValue("http://clinical-service") URI baseUrl) {

	public ClinicalClientProperties {
		if (baseUrl == null || baseUrl.getScheme() == null) {
			throw new IllegalArgumentException("Clinical client URL is invalid");
		}
	}
}
