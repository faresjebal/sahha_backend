package com.sahha.communication.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("sahha.communication.websocket")
public record CommunicationWebSocketProperties(
        @DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    public CommunicationWebSocketProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()
                || allowedOrigins.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Communication WebSocket origins are required");
        }
        allowedOrigins = allowedOrigins.stream().map(String::strip).map(URI::create)
                .peek(CommunicationWebSocketProperties::requireOrigin).map(URI::toString).distinct().toList();
    }
    private static void requireOrigin(URI origin) {
        if (origin.getHost() == null || !("http".equalsIgnoreCase(origin.getScheme())
                || "https".equalsIgnoreCase(origin.getScheme())) || origin.getUserInfo() != null
                || (origin.getPath() != null && !origin.getPath().isEmpty())
                || origin.getQuery() != null || origin.getFragment() != null) {
            throw new IllegalArgumentException("Communication WebSocket requires exact HTTP(S) origins");
        }
    }
}
