package com.sahha.patient.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PatientIdentifierProperties.class)
public class PatientIdentifierConfiguration {
}
