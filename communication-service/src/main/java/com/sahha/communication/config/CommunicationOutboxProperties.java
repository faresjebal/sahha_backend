package com.sahha.communication.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.communication.outbox")
public record CommunicationOutboxProperties(
		@DefaultValue("sahha.communication.messages.v1") String topic,
		@DefaultValue("sahha.communication.referrals.v1") String referralsTopic,
		@DefaultValue("50") int batchSize,
		@DefaultValue("PT5S") Duration sendTimeout) {
	public CommunicationOutboxProperties {
		if (topic == null || topic.isBlank() || referralsTopic == null
				|| referralsTopic.isBlank() || batchSize < 1 || batchSize > 1000
				|| sendTimeout == null || sendTimeout.isZero() || sendTimeout.isNegative()) {
			throw new IllegalArgumentException("Communication outbox properties are invalid");
		}
	}
}
