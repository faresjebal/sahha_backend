package com.sahha.organisation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AuthAccountClientProperties.class)
public class AuthAccountClientConfiguration {

	@Bean
	@Primary
	RestClient.Builder organisationPlainRestClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	@LoadBalanced
	RestClient.Builder organisationLoadBalancedRestClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	RestClient authAccountRestClient(
			@LoadBalanced RestClient.Builder builder,
			AuthAccountClientProperties properties) {
		return builder.baseUrl(properties.baseUrl().toString()).build();
	}
}
