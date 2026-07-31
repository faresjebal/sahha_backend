package com.sahha.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(AuthOutboxProperties.class)
public class AuthOutboxConfiguration {

	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(
			prefix = "sahha.auth.outbox",
			name = "publisher-enabled",
			havingValue = "true")
	static class SchedulingConfiguration {
	}
}
