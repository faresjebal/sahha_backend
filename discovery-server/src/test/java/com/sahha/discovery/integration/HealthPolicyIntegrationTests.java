package com.sahha.discovery.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Tests the shipped local policy, not a separate set of test-only health defaults. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:health-policy.properties")
class HealthPolicyIntegrationTests {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired HealthContributorRegistry contributors;

    @ParameterizedTest
    @ValueSource(strings = { "/actuator/health/liveness", "/actuator/health/readiness" })
    void probesIgnoreBrowserCredentialsAndExposeOnlyStatus(String path) throws Exception {
        mvc.perform(get(path).cookie(new Cookie("SAHHA_ACCESS_TOKEN", "synthetic-invalid-token"),
                        new Cookie("JSESSIONID", "synthetic-invalid-session"))
                        .header("Authorization", "Bearer synthetic-invalid-token"))
                .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/actuator/health/db", "/actuator/health/readiness/db",
            "/actuator/env", "/actuator/configprops", "/actuator", "/actuator/shutdown" })
    void componentAndOtherActuatorPathsAreClosed(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @Test
    void operationalWritesAreDeniedWithoutCreatingSessions() throws Exception {
        mvc.perform(post("/actuator/health")).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void optionalFailureDoesNotRemoveLocalReadinessOrLiveness() throws Exception {
        contributors.registerContributor("syntheticOptional", (HealthIndicator) () ->
                Health.down().withDetail("private", "synthetic-sensitive-detail").build());
        try {
            mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
            probe("readiness", 200, "UP");
            probe("liveness", 200, "UP");
        } finally {
            contributors.unregisterContributor("syntheticOptional");
        }
    }

    @Test
    void refusingTrafficIsNotAReasonToRestartAndCanRecover() throws Exception {
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
    void brokenLivenessReturns503AndCanRecover() throws Exception {
        AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
        try {
            probe("liveness", 503, "DOWN");
        } finally {
            AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
        }
        probe("liveness", 200, "UP");
    }

    private void probe(String group, int code, String state) throws Exception {
        mvc.perform(get("/actuator/health/" + group)).andExpect(status().is(code))
                .andExpect(jsonPath("$.status").value(state))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
