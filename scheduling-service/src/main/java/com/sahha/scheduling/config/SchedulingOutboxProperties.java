package com.sahha.scheduling.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.scheduling.outbox")
public record SchedulingOutboxProperties(
		@DefaultValue("false") boolean publisherEnabled,
		@DefaultValue("sahha.scheduling.appointments.v1") String topic,
		@DefaultValue("50") int batchSize,
		@DefaultValue("PT1S") Duration fixedDelay,
		@DefaultValue("PT5S") Duration sendTimeout,
		@DefaultValue("PT5M") Duration claimLease,
		@DefaultValue("PT5S") Duration initialRetryDelay,
		@DefaultValue("PT5M") Duration maximumRetryDelay) {

	public SchedulingOutboxProperties {
		if (topic == null || topic.isBlank()) {
			throw new IllegalArgumentException("outbox topic must not be blank");
		}
		if (batchSize < 1 || batchSize > 500) {
			throw new IllegalArgumentException(
					"outbox batchSize must be between 1 and 500");
		}
		requirePositive(fixedDelay, "fixedDelay");
		requirePositive(sendTimeout, "sendTimeout");
		requirePositive(claimLease, "claimLease");
		requirePositive(initialRetryDelay, "initialRetryDelay");
		requirePositive(maximumRetryDelay, "maximumRetryDelay");
		if (initialRetryDelay.compareTo(maximumRetryDelay) > 0) {
			throw new IllegalArgumentException(
					"initialRetryDelay must not exceed maximumRetryDelay");
		}
		Duration maximumBatchSendTime;
		try {
			maximumBatchSendTime = sendTimeout.multipliedBy(batchSize);
		}
		catch (ArithmeticException overflow) {
			throw new IllegalArgumentException(
					"batch send window exceeds the supported duration", overflow);
		}
		if (claimLease.compareTo(maximumBatchSendTime) <= 0) {
			throw new IllegalArgumentException(
					"claimLease must exceed sendTimeout multiplied by batchSize");
		}
	}

	private static void requirePositive(Duration duration, String name) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
