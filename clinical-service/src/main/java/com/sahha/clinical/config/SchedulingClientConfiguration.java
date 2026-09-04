package com.sahha.clinical.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SchedulingClientProperties.class)
public class SchedulingClientConfiguration {

	@Bean
	@Primary
	RestClient.Builder clinicalPlainRestClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	@LoadBalanced
	RestClient.Builder clinicalLoadBalancedRestClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	RestClient clinicalSchedulingRestClient(
			@LoadBalanced RestClient.Builder builder,
			SchedulingClientProperties properties) {
		return builder.baseUrl(properties.baseUrl().toString()).build();
	}
}
