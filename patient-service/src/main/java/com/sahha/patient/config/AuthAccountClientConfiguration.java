package com.sahha.patient.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AuthAccountClientProperties.class)
public class AuthAccountClientConfiguration {

	@Bean
	RestClient patientAuthAccountRestClient(
			@LoadBalanced RestClient.Builder builder,
			AuthAccountClientProperties properties) {
		return builder.baseUrl(properties.baseUrl().toString()).build();
	}
}
