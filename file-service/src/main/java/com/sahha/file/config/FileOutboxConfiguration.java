package com.sahha.file.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(FileOutboxProperties.class)
public class FileOutboxConfiguration {

	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(
			prefix = "sahha.file.outbox",
			name = "publisher-enabled",
			havingValue = "true")
	static class SchedulingConfiguration {
	}
}
