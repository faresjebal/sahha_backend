package com.sahha.communication.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({OrganisationClientProperties.class,
		SchedulingClientProperties.class})
public class DownstreamClientConfiguration {
	@Bean
	@Primary
	RestClient.Builder communicationPlainRestClientBuilder() { return RestClient.builder(); }
	@Bean
	@LoadBalanced
	RestClient.Builder communicationLoadBalancedRestClientBuilder() { return RestClient.builder(); }
	@Bean
	RestClient communicationOrganisationRestClient(@LoadBalanced RestClient.Builder builder,
			OrganisationClientProperties properties) {
		return builder.baseUrl(properties.baseUrl().toString()).build();
	}
	@Bean
	RestClient communicationSchedulingRestClient(@LoadBalanced RestClient.Builder builder,
			SchedulingClientProperties properties) {
		return builder.baseUrl(properties.baseUrl().toString()).build();
	}
}
