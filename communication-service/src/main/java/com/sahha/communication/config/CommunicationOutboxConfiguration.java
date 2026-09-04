package com.sahha.communication.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(CommunicationOutboxProperties.class)
public class CommunicationOutboxConfiguration {
	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(prefix = "sahha.communication.outbox",
			name = "publisher-enabled", havingValue = "true")
	static class SchedulingConfiguration { }
}
