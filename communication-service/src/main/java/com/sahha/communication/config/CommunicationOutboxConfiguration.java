package com.sahha.communication.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(CommunicationOutboxProperties.class)
@EnableScheduling
public class CommunicationOutboxConfiguration {
}
