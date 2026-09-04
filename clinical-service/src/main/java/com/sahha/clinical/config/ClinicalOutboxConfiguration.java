package com.sahha.clinical.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(ClinicalOutboxProperties.class)
public class ClinicalOutboxConfiguration {

	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(
			prefix = "sahha.clinical.outbox",
			name = "publisher-enabled",
			havingValue = "true")
	static class SchedulingConfiguration {
	}
}
