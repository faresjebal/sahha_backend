package com.sahha.communication.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sahha.communication.scheduling-client")
public record SchedulingClientProperties(URI baseUrl) {
	public SchedulingClientProperties {
		if (baseUrl == null || baseUrl.getScheme() == null) {
			throw new IllegalArgumentException("Scheduling client base URL is invalid");
		}
	}
}
