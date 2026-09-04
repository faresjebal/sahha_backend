package com.sahha.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayHttpClientProperties.class)
public class GatewayHttpClientConfiguration {

	@Bean
	HttpClientCustomizer gatewayHttpClientCustomizer(
			GatewayHttpClientProperties properties) {
		int connectTimeoutMillis = Math.toIntExact(
				properties.connectTimeout().toMillis());
		return httpClient -> httpClient
				.option(
						ChannelOption.CONNECT_TIMEOUT_MILLIS,
						connectTimeoutMillis)
				.responseTimeout(properties.readTimeout())
				.followRedirect(false);
	}
}
