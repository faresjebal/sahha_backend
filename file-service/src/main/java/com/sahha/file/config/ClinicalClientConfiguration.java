package com.sahha.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ClinicalClientProperties.class)
public class ClinicalClientConfiguration {

	@Bean
	@Primary
	RestClient.Builder filePlainRestClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	@LoadBalanced
	RestClient.Builder fileLoadBalancedRestClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	RestClient fileClinicalRestClient(
			@LoadBalanced RestClient.Builder builder,
			ClinicalClientProperties properties) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        return builder.requestFactory(factory).baseUrl(properties.baseUrl().toString()).build();
	}
}
