package com.sahha.audit.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.sahha.audit.fixture.AuditTestDatabaseConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(AuditTestDatabaseConfiguration.class)
class AuditHttpIsolationIntegrationTests {

	@LocalServerPort int port;
	@Autowired MockMvc mvc;
	@Autowired HealthContributorRegistry contributors;

	@ParameterizedTest
	@ValueSource(strings = { "/actuator/health", "/actuator/health/liveness",
			"/actuator/health/readiness", "/actuator/info" })
	void realHttpAllowsOnlyMinimalOperationalReads(String path) throws Exception {
		var response = read(path);
		assertEquals(200, response.statusCode());
		assertFalse(response.body().contains("components"));
		assertFalse(response.body().contains("details"));
		assertFalse(response.body().contains("jdbc:"));
		assertTrue(response.headers().allValues("set-cookie").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/audit", "/api/v1/audit/events", "/api/v1/internal/audit/events",
			"/actuator/env", "/actuator/health/db", "/v3/api-docs", "/login" })
	void realHttpDeniesUnimplementedApisAndNonPublicOperations(String path) throws Exception {
		var response = read(path);
		assertEquals(403, response.statusCode());
		assertTrue(response.body().isEmpty());
		assertTrue(response.headers().allValues("set-cookie").isEmpty());
		assertTrue(response.headers().allValues("www-authenticate").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "PLATFORM_ADMIN", "ORGANIZATION_ADMIN", "DOCTOR", "RECEPTIONIST", "PATIENT" })
	void evenAnAlreadyAuthenticatedRoleCannotAccessAudit(String role) throws Exception {
		mvc.perform(get("/api/v1/audit/events").with(user("synthetic-user").roles(role)))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/audit/events").with(user("synthetic-user").roles(role)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void operationalRoutesDoNotPermitWritesEvenWithCsrf() throws Exception {
		mvc.perform(post("/actuator/health").with(csrf())).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "POST", "PUT", "PATCH", "DELETE" })
	void realHttpDeniesWritesWithoutCreatingSessionCookies(String method) throws Exception {
		try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
			for (String path : new String[] { "/api/v1/audit/events", "/actuator/health" }) {
				var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
						.timeout(Duration.ofSeconds(5)).method(method, HttpRequest.BodyPublishers.noBody())
						.build(), HttpResponse.BodyHandlers.ofString());
				assertEquals(403, response.statusCode());
				assertTrue(response.headers().allValues("set-cookie").isEmpty());
			}
		}
	}

	@Test
	void readinessFailsWhenDatabaseHealthFailsButLivenessRemainsUp() throws Exception {
		var database = contributors.unregisterContributor("db");
		assertNotNull(database);
		try {
			contributors.registerContributor("db", (HealthIndicator) () ->
					Health.down().withDetail("private", "synthetic-database-failure").build());
			assertEquals(503, read("/actuator/health").statusCode());
			var readiness = read("/actuator/health/readiness");
			assertEquals(503, readiness.statusCode());
			assertFalse(readiness.body().contains("synthetic-database-failure"));
			assertEquals(200, read("/actuator/health/liveness").statusCode());
		} finally {
			contributors.unregisterContributor("db");
			contributors.registerContributor("db", database);
		}
		assertEquals(200, read("/actuator/health/readiness").statusCode());
	}

	private HttpResponse<String> read(String path) throws Exception {
		try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
				.followRedirects(HttpClient.Redirect.NEVER).build()) {
			return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
					.timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
		}
	}
}
