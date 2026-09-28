package com.sahha.clinical.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.clinical.communication-client")
public record CommunicationClientProperties(
        @DefaultValue("http://communication-service") URI baseUrl) {
    public CommunicationClientProperties {
        if (baseUrl == null || baseUrl.getHost() == null
                || !java.util.Set.of("http", "https").contains(baseUrl.getScheme())) {
            throw new IllegalArgumentException("Communication client base URL is invalid");
        }
    }
}
