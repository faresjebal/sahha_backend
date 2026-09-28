package com.sahha.gateway.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:health-policy.properties")
class HealthPolicyIntegrationTests {

    @LocalServerPort int port;
    @Autowired ApplicationContext context;
    @Autowired ReactiveHealthContributorRegistry contributors;

    private WebTestClient http() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/actuator/health/liveness", "/actuator/health/readiness" })
    void probesIgnoreBrowserCredentialsAndExposeOnlyStatus(String path) {
        http().get().uri(path).cookie("SAHHA_ACCESS_TOKEN", "synthetic-invalid-token")
                .cookie("JSESSIONID", "synthetic-invalid-session")
                .header("Authorization", "Bearer synthetic-invalid-token")
                .exchange().expectStatus().isOk().expectHeader().doesNotExist("Set-Cookie")
                .expectBody().jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist().jsonPath("$.details").doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/actuator/health/db", "/actuator/health/readiness/db",
            "/actuator/env", "/actuator/configprops", "/actuator", "/actuator/shutdown" })
    void componentAndOtherActuatorPathsAreClosed(String path) {
        http().get().uri(path).exchange().expectStatus().isForbidden();
    }

    @Test
    void operationalWritesAreDeniedWithoutCreatingSessions() {
        http().post().uri("/actuator/health").exchange().expectStatus().isForbidden()
                .expectHeader().doesNotExist("Set-Cookie");
    }

    @Test
    void optionalFailureDoesNotRemoveLocalReadinessOrLiveness() {
        contributors.registerContributor("syntheticOptional", (ReactiveHealthIndicator) () ->
                Mono.just(Health.down().withDetail("private", "synthetic-sensitive-detail").build()));
        try {
            http().get().uri("/actuator/health").exchange().expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.components").doesNotExist().jsonPath("$.details").doesNotExist();
            probe("readiness", 200, "UP");
            probe("liveness", 200, "UP");
        } finally {
            contributors.unregisterContributor("syntheticOptional");
        }
    }

    @Test
    void refusingTrafficIsNotAReasonToRestartAndCanRecover() {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        try {
            probe("readiness", 503, "OUT_OF_SERVICE");
            probe("liveness", 200, "UP");
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        }
        probe("readiness", 200, "UP");
    }

    @Test
    void brokenLivenessReturns503AndCanRecover() {
        AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
        try {
            probe("liveness", 503, "DOWN");
        } finally {
            AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
        }
        probe("liveness", 200, "UP");
    }

    private void probe(String group, int code, String state) {
        http().get().uri("/actuator/health/" + group).exchange().expectStatus().isEqualTo(code)
                .expectBody().jsonPath("$.status").isEqualTo(state)
                .jsonPath("$.components").doesNotExist().jsonPath("$.details").doesNotExist();
    }
}
