package com.sahha.clinical.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.clinical.scheduling-client")
public record SchedulingClientProperties(
		@DefaultValue("http://scheduling-service") URI baseUrl) {

	public SchedulingClientProperties {
		if (baseUrl == null || baseUrl.getScheme() == null) {
			throw new IllegalArgumentException("Scheduling client base URL is invalid");
		}
	}
}
