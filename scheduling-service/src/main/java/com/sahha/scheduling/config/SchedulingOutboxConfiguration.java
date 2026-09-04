package com.sahha.scheduling.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(SchedulingOutboxProperties.class)
public class SchedulingOutboxConfiguration {

	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(
			prefix = "sahha.scheduling.outbox",
			name = "publisher-enabled",
			havingValue = "true")
	static class SchedulingConfiguration {
	}
}
