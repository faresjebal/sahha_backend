package com.sahha.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OrganisationContextClientProperties.class)
public class OrganisationContextClientConfiguration {

	@Bean
	@Primary
	RestClient.Builder notificationPlainRestClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	@LoadBalanced
	RestClient.Builder notificationLoadBalancedRestClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	RestClient notificationOrganisationContextRestClient(
			@LoadBalanced RestClient.Builder builder,
			OrganisationContextClientProperties properties) {
		return builder.baseUrl(properties.baseUrl().toString()).build();
	}
}
