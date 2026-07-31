package com.sahha.organisation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.organisation.outbox")
public record OrganisationOutboxProperties(
		@DefaultValue("false") boolean publisherEnabled,
		@DefaultValue("sahha.organisation.events.v1") String topic,
		@DefaultValue("50") int batchSize,
		@DefaultValue("PT1S") Duration fixedDelay,
		@DefaultValue("PT5S") Duration sendTimeout,
		@DefaultValue("PT5S") Duration initialRetryDelay,
		@DefaultValue("PT5M") Duration maximumRetryDelay) {

	public OrganisationOutboxProperties {
		if (topic == null || topic.isBlank()) {
			throw new IllegalArgumentException("outbox topic must not be blank");
		}
		if (batchSize < 1 || batchSize > 500) {
			throw new IllegalArgumentException(
					"outbox batchSize must be between 1 and 500");
		}
		requirePositive(fixedDelay, "fixedDelay");
		requirePositive(sendTimeout, "sendTimeout");
		requirePositive(initialRetryDelay, "initialRetryDelay");
		requirePositive(maximumRetryDelay, "maximumRetryDelay");
		if (initialRetryDelay.compareTo(maximumRetryDelay) > 0) {
			throw new IllegalArgumentException(
					"initialRetryDelay must not exceed maximumRetryDelay");
		}
	}

	private static void requirePositive(Duration duration, String name) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
