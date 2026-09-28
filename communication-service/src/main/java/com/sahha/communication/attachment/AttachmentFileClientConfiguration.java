package com.sahha.communication.attachment;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
@Configuration
public class AttachmentFileClientConfiguration {
    @Bean
    RestClient communicationAttachmentFileClient(@LoadBalanced RestClient.Builder builder,
            @Value("${sahha.communication.file.base-url:http://file-service}") String baseUrl) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
