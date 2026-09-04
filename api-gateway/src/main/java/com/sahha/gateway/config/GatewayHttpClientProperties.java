package com.sahha.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.gateway.http-client")
public record GatewayHttpClientProperties(
		@DefaultValue("PT5S") Duration connectTimeout,
		@DefaultValue("PT30S") Duration readTimeout) {

	public GatewayHttpClientProperties {
		requirePositive(connectTimeout, "connectTimeout");
		requirePositive(readTimeout, "readTimeout");
	}

	private static void requirePositive(Duration value, String fieldName) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(
					fieldName + " must be positive");
		}
		if ("connectTimeout".equals(fieldName)
				&& value.toMillis() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(
					"connectTimeout is too large");
		}
	}
}
