package com.sahha.patient.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(PatientOutboxProperties.class)
public class PatientOutboxConfiguration {

	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(
			prefix = "sahha.patient.outbox",
			name = "publisher-enabled",
			havingValue = "true")
	static class SchedulingConfiguration {
	}
}
