package com.sahha.file.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CommunicationClientProperties.class)
public class CommunicationClientConfiguration {
    @Bean
    RestClient fileCommunicationRestClient(@LoadBalanced RestClient.Builder builder,
            CommunicationClientProperties properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return builder.clone().baseUrl(properties.baseUrl().toString())
                .requestFactory(factory).build();
    }
}
