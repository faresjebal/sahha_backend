package com.sahha.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(AuthSessionRetentionProperties.class)
public class AuthSessionRetentionConfiguration {

	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(
			prefix = "sahha.auth.session-retention",
			name = "cleanup-enabled",
			havingValue = "true",
			matchIfMissing = true)
	static class SchedulingConfiguration {
	}
}
