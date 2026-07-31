package com.sahha.gateway.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sahha.gateway.filter.GatewayRequestContextFilter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewaySecurityRoutingIntegrationTests {

	private static final String VALID_TOKEN = "aaa.bbb.ccc";
	private static final String INVALID_TOKEN = "ddd.eee.fff";
	private static final String USER_TOKEN = "ggg.hhh.iii";
	private static final AtomicInteger UPSTREAM_REQUESTS =
			new AtomicInteger();
	private static final AtomicReference<Headers> LAST_UPSTREAM_HEADERS =
			new AtomicReference<>();
	private static final AtomicReference<String> LAST_UPSTREAM_PATH =
			new AtomicReference<>();
	private static final CopyOnWriteArrayList<String> UPSTREAM_COOKIES =
			new CopyOnWriteArrayList<>();
	private static final HttpServer AUTH_SERVER = startAuthServer();
	private static final HttpServer ORGANISATION_SERVER =
			startOrganisationServer();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@DynamicPropertySource
	static void authServiceInstance(DynamicPropertyRegistry registry) {
		registry.add(
				"spring.cloud.discovery.client.simple.instances.auth-service[0].uri",
				() -> "http://127.0.0.1:"
						+ AUTH_SERVER.getAddress().getPort());
		registry.add(
				"spring.cloud.discovery.client.simple.instances.organisation-service[0].uri",
				() -> "http://127.0.0.1:"
						+ ORGANISATION_SERVER.getAddress().getPort());
	}

	@BeforeEach
	void resetUpstreamObservations() {
		UPSTREAM_REQUESTS.set(0);
		LAST_UPSTREAM_HEADERS.set(null);
		LAST_UPSTREAM_PATH.set(null);
		UPSTREAM_COOKIES.clear();
	}

	@AfterAll
	static void stopAuthServer() {
		AUTH_SERVER.stop(0);
		ORGANISATION_SERVER.stop(0);
	}

	@Test
	void publicAuthRouteIgnoresStaleAccessAndSanitizesEdgeHeaders()
			throws Exception {
		mockMvc.perform(get("/api/v1/auth/csrf")
						.cookie(new Cookie(
								"SAHHA_ACCESS_TOKEN",
								"expired-or-malformed"))
						.header(
								GatewayRequestContextFilter.REQUEST_ID_HEADER,
								"request-12345")
						.header(
								GatewayRequestContextFilter.CLIENT_IP_HEADER,
								"203.0.113.250")
						.header("Authorization", "Bearer attacker-token")
						.header("X-Sahha-User-Id", "attacker-user"))
				.andExpect(status().isOk())
				.andExpect(header().string(
						GatewayRequestContextFilter.REQUEST_ID_HEADER,
						"request-12345"))
				.andExpect(header().string(
						HttpHeaders.SET_COOKIE,
						"XSRF-TOKEN=synthetic-csrf; Path=/"))
				.andExpect(header().string(
						"Content-Security-Policy",
						"default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
				.andExpect(content().json(
						"{\"token\":\"synthetic-csrf\"}"));

		org.junit.jupiter.api.Assertions.assertEquals(
				"request-12345",
				LAST_UPSTREAM_HEADERS.get().getFirst("X-Request-ID"));
		org.junit.jupiter.api.Assertions.assertNull(
				LAST_UPSTREAM_HEADERS.get().getFirst("Authorization"));
		org.junit.jupiter.api.Assertions.assertNull(
				LAST_UPSTREAM_HEADERS.get().getFirst("X-Sahha-User-Id"));
		org.junit.jupiter.api.Assertions.assertNotEquals(
				"203.0.113.250",
				LAST_UPSTREAM_HEADERS.get().getFirst(
						GatewayRequestContextFilter.CLIENT_IP_HEADER));
		org.junit.jupiter.api.Assertions.assertNotNull(
				LAST_UPSTREAM_HEADERS.get().getFirst(
						GatewayRequestContextFilter.CLIENT_IP_HEADER));

		mockMvc.perform(get("/api/v1/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(header().string(
						HttpHeaders.SET_COOKIE,
						"XSRF-TOKEN=synthetic-csrf; Path=/"));

		verifyNoInteractions(jwtDecoder);
		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/auth/csrf",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of(
						"SAHHA_ACCESS_TOKEN=expired-or-malformed",
						"<none>"),
				UPSTREAM_COOKIES);
	}

	@Test
	void protectedAuthRouteRequiresAValidAccessCookie()
			throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(
						HttpHeaders.WWW_AUTHENTICATE,
						"Bearer"))
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:authentication-required"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());

		org.junit.jupiter.api.Assertions.assertEquals(
				0,
				UPSTREAM_REQUESTS.get());
	}

	@Test
	void validCookieIsDecodedAndTheProtectedRequestIsForwarded()
			throws Exception {
		when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(validJwt(VALID_TOKEN));

		mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(new Cookie(
								"SAHHA_ACCESS_TOKEN",
								VALID_TOKEN)))
				.andExpect(status().isNoContent());

		verify(jwtDecoder).decode(VALID_TOKEN);
		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/auth/logout",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				1,
				UPSTREAM_REQUESTS.get());
	}

	@Test
	void invalidSignatureOrClaimsAreRejectedBeforeRouting()
			throws Exception {
		when(jwtDecoder.decode(INVALID_TOKEN)).thenThrow(
				new JwtValidationException(
						"synthetic invalid token",
						List.of(new OAuth2Error(
								"invalid_token",
								"synthetic invalid token",
								null))));

		mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(new Cookie(
								"SAHHA_ACCESS_TOKEN",
								INVALID_TOKEN)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));

		verify(jwtDecoder).decode(INVALID_TOKEN);
		org.junit.jupiter.api.Assertions.assertEquals(
				0,
				UPSTREAM_REQUESTS.get());
	}

	@Test
	void platformOrganisationRouteEnforcesRoleBeforeForwarding()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(
				validJwt(USER_TOKEN, List.of()));

		mockMvc.perform(get("/api/v1/platform/organisations")
						.cookie(new Cookie(
								"SAHHA_ACCESS_TOKEN",
								USER_TOKEN)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:request-forbidden"));
		org.junit.jupiter.api.Assertions.assertEquals(
				0,
				UPSTREAM_REQUESTS.get());

		when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(
				validJwt(VALID_TOKEN, List.of("PLATFORM_ADMIN")));
		mockMvc.perform(get("/api/v1/platform/organisations")
						.cookie(new Cookie(
								"SAHHA_ACCESS_TOKEN",
								VALID_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(content().json(
						"{\"items\":[],\"totalElements\":0}"));

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/platform/organisations",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + VALID_TOKEN),
				UPSTREAM_COOKIES);
		org.junit.jupiter.api.Assertions.assertEquals(
				1,
				UPSTREAM_REQUESTS.get());
	}

	@Test
	void configuredFrontendReceivesCredentialedCorsPreflight()
			throws Exception {
		mockMvc.perform(options("/api/v1/auth/login")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(
								HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
								"POST")
						.header(
								HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
								"content-type,x-xsrf-token"))
				.andExpect(status().isOk())
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://localhost:5173"))
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
						"true"));

		verifyNoInteractions(jwtDecoder);
		org.junit.jupiter.api.Assertions.assertEquals(
				0,
				UPSTREAM_REQUESTS.get());
	}

	private static Jwt validJwt(String tokenValue) {
		return validJwt(tokenValue, List.of("PLATFORM_ADMIN"));
	}

	private static Jwt validJwt(
			String tokenValue,
			List<String> roles) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(tokenValue)
				.header("alg", "RS256")
				.subject(UUID.randomUUID().toString())
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", roles)
				.claim("token_type", "access")
				.build();
	}

	private static HttpServer startAuthServer() {
		try {
			HttpServer server = HttpServer.create(
					new InetSocketAddress(
							InetAddress.getLoopbackAddress(),
							0),
					0);
			server.createContext("/", GatewaySecurityRoutingIntegrationTests::
					handleAuthRequest);
			server.start();
			return server;
		}
		catch (IOException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}

	private static HttpServer startOrganisationServer() {
		try {
			HttpServer server = HttpServer.create(
					new InetSocketAddress(
							InetAddress.getLoopbackAddress(),
							0),
					0);
			server.createContext(
					"/",
					GatewaySecurityRoutingIntegrationTests::
							handleOrganisationRequest);
			server.start();
			return server;
		}
		catch (IOException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}

	private static void handleAuthRequest(HttpExchange exchange)
			throws IOException {
		UPSTREAM_REQUESTS.incrementAndGet();
		LAST_UPSTREAM_HEADERS.set(exchange.getRequestHeaders());
		LAST_UPSTREAM_PATH.set(exchange.getRequestURI().getPath());
		UPSTREAM_COOKIES.add(
				java.util.Optional.ofNullable(
								exchange.getRequestHeaders()
										.getFirst(HttpHeaders.COOKIE))
						.orElse("<none>"));
		exchange.getResponseHeaders().set(
				GatewayRequestContextFilter.REQUEST_ID_HEADER,
				exchange.getRequestHeaders().getFirst(
						GatewayRequestContextFilter.REQUEST_ID_HEADER));
		if ("/api/v1/auth/csrf".equals(exchange.getRequestURI().getPath())) {
			byte[] body = "{\"token\":\"synthetic-csrf\"}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set(
					HttpHeaders.CONTENT_TYPE,
					"application/json");
			exchange.getResponseHeaders().add(
					HttpHeaders.SET_COOKIE,
					"XSRF-TOKEN=synthetic-csrf; Path=/");
			exchange.sendResponseHeaders(200, body.length);
			try (var output = exchange.getResponseBody()) {
				output.write(body);
			}
			return;
		}
		exchange.sendResponseHeaders(204, -1);
		exchange.close();
	}

	private static void handleOrganisationRequest(HttpExchange exchange)
			throws IOException {
		UPSTREAM_REQUESTS.incrementAndGet();
		LAST_UPSTREAM_HEADERS.set(exchange.getRequestHeaders());
		LAST_UPSTREAM_PATH.set(exchange.getRequestURI().getPath());
		UPSTREAM_COOKIES.add(
				java.util.Optional.ofNullable(
								exchange.getRequestHeaders()
										.getFirst(HttpHeaders.COOKIE))
						.orElse("<none>"));
		byte[] body = """
				{"items":[],"totalElements":0}
				""".strip().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set(
				HttpHeaders.CONTENT_TYPE,
				"application/json");
		exchange.sendResponseHeaders(200, body.length);
		try (var output = exchange.getResponseBody()) {
			output.write(body);
		}
	}
}
