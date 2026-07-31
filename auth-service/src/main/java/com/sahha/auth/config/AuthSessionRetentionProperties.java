package com.sahha.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.auth.session-retention")
public record AuthSessionRetentionProperties(
		@DefaultValue("true") boolean cleanupEnabled,
		@DefaultValue("P7D") Duration refreshTokenRetention,
		@DefaultValue("100") int familyBatchSize,
		@DefaultValue("PT1H") Duration fixedDelay) {

	public AuthSessionRetentionProperties {
		if (refreshTokenRetention == null
				|| refreshTokenRetention.isNegative()) {
			throw new IllegalArgumentException(
					"refreshTokenRetention must not be negative");
		}
		if (familyBatchSize < 1 || familyBatchSize > 1000) {
			throw new IllegalArgumentException(
					"familyBatchSize must be between 1 and 1000");
		}
		if (fixedDelay == null
				|| fixedDelay.isZero()
				|| fixedDelay.isNegative()) {
			throw new IllegalArgumentException(
					"fixedDelay must be positive");
		}
	}
}
