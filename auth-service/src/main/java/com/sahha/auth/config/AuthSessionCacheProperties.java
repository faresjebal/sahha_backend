package com.sahha.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.auth.session-cache")
public record AuthSessionCacheProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("PT30S") Duration maximumActiveTtl) {

	public AuthSessionCacheProperties {
		if (maximumActiveTtl == null
				|| maximumActiveTtl.isZero()
				|| maximumActiveTtl.isNegative()) {
			throw new IllegalArgumentException(
					"maximumActiveTtl must be positive");
		}
	}
}
