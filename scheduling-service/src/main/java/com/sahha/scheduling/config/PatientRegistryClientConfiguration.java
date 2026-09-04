package com.sahha.scheduling.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PatientRegistryClientProperties.class)
public class PatientRegistryClientConfiguration {

	@Bean
	RestClient schedulingPatientRegistryRestClient(
			@LoadBalanced RestClient.Builder builder,
			PatientRegistryClientProperties properties) {
		return builder.baseUrl(properties.baseUrl().toString()).build();
	}
}
