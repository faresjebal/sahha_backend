package com.sahha.file.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.file.outbox")
public record FileOutboxProperties(
		@DefaultValue("false") boolean publisherEnabled,
		@DefaultValue("sahha.file.medical-files.v1") String topic,
		@DefaultValue("50") int batchSize,
		@DefaultValue("PT1S") Duration fixedDelay,
		@DefaultValue("PT5S") Duration sendTimeout,
		@DefaultValue("PT1M") Duration claimLease,
		@DefaultValue("PT5S") Duration initialRetryDelay,
		@DefaultValue("PT5M") Duration maximumRetryDelay) {

	public FileOutboxProperties {
		if (topic == null || topic.isBlank() || batchSize < 1 || batchSize > 500) {
			throw new IllegalArgumentException("File outbox properties are invalid");
		}
		requirePositive(fixedDelay);
		requirePositive(sendTimeout);
		requirePositive(claimLease);
		requirePositive(initialRetryDelay);
		requirePositive(maximumRetryDelay);
		if (initialRetryDelay.compareTo(maximumRetryDelay) > 0) {
			throw new IllegalArgumentException("File outbox retry range is invalid");
		}
	}

	private static void requirePositive(Duration value) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("File outbox duration is invalid");
		}
	}
}
