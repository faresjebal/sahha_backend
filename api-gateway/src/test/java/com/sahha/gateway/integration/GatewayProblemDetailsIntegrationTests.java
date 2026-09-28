package com.sahha.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayProblemDetailsIntegrationTests {
    @Autowired private WebTestClient client;

    @Test
    void missingDiscoveredServiceReturnsSafeCorrelated503OverRealHttp() {
        client.get().uri("/api/v1/auth/csrf?secret=synthetic-private")
                .header("X-Request-ID", "synthetic-request-123").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith("application/problem+json")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().doesNotExist("Set-Cookie")
                .expectHeader().valueEquals("X-Request-ID", "synthetic-request-123")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sahha:problem:http-503")
                .jsonPath("$.instance").isEqualTo("/api/v1/auth/csrf")
                .jsonPath("$.requestId").isEqualTo("synthetic-request-123")
                .jsonPath("$.detail").isEqualTo("The required service is temporarily unavailable.")
                .jsonPath("$.trace").doesNotExist();
    }
}
