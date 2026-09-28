package com.sahha.notification.patient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PatientNotificationClientConfiguration {
    @Bean
    RestClient notificationPatientRestClient(@LoadBalanced RestClient.Builder builder,
            @Value("${sahha.notification.patient.base-url:http://patient-service}") URI uri) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return builder.clone().baseUrl(uri.toString()).requestFactory(factory).build();
    }
}
