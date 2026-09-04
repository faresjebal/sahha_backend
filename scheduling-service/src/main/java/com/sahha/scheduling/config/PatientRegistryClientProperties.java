package com.sahha.scheduling.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("sahha.scheduling.patient-client")
public record PatientRegistryClientProperties(
		@DefaultValue("http://patient-service") URI baseUrl) {
}
