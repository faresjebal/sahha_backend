package com.sahha.clinical.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.clinical.organisation-client")
public record OrganisationClientProperties(@DefaultValue("http://organisation-service") URI baseUrl) {
    public OrganisationClientProperties {
        if (baseUrl == null || baseUrl.getScheme() == null || !java.util.Set.of("http", "https").contains(baseUrl.getScheme())
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("Organisation client base URL is invalid");
        }
    }
}
