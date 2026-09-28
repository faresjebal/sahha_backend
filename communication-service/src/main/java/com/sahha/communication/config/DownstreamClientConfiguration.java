package com.sahha.communication.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({OrganisationClientProperties.class,
		SchedulingClientProperties.class, ClinicalClientProperties.class})
public class DownstreamClientConfiguration {
	@Bean
	RestClient communicationClinicalRestClient(@LoadBalanced RestClient.Builder builder,
			ClinicalClientProperties properties) {
		var http = java.net.http.HttpClient.newBuilder()
				.connectTimeout(java.time.Duration.ofSeconds(2))
				.followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
		var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(java.time.Duration.ofSeconds(3));
		return builder.clone().baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
	}
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
