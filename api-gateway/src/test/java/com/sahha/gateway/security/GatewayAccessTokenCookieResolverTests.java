package com.sahha.gateway.security;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import com.sahha.gateway.config.GatewaySecurityProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayAccessTokenCookieResolverTests {

	private final GatewayAccessTokenCookieResolver resolver =
			new GatewayAccessTokenCookieResolver(properties());

	@Test
	void protectedRequestUsesExactlyOneWellFormedAccessCookie() {
		MockServerWebExchange exchange = exchange(
				MockServerHttpRequest.post("/api/v1/auth/logout")
						.cookie(new HttpCookie(
								"SAHHA_ACCESS_TOKEN", "aaa.bbb.ccc")));

		BearerTokenAuthenticationToken authentication =
				(BearerTokenAuthenticationToken) resolver.convert(exchange).block();
		assertEquals("aaa.bbb.ccc", authentication.getToken());
	}

	@Test
	void publicRefreshIgnoresAnExpiredOrMalformedAccessCookie() {
		MockServerWebExchange exchange = exchange(
				MockServerHttpRequest.post("/api/v1/auth/refresh")
						.cookie(new HttpCookie(
								"SAHHA_ACCESS_TOKEN", "not-a-jwt")));

		assertNull(resolver.convert(exchange).block());
	}

	@Test
	void protectedRequestRejectsMalformedAndDuplicateCookies() {
		MockServerWebExchange malformed = exchange(
				MockServerHttpRequest.post("/api/v1/auth/logout")
						.cookie(new HttpCookie(
								"SAHHA_ACCESS_TOKEN", "not-a-jwt")));
		assertThrows(
				OAuth2AuthenticationException.class,
				() -> resolver.convert(malformed).block());

		MockServerWebExchange duplicate = exchange(
				MockServerHttpRequest.post("/api/v1/auth/logout")
						.cookie(
								new HttpCookie(
										"SAHHA_ACCESS_TOKEN", "aaa.bbb.ccc"),
								new HttpCookie(
										"SAHHA_ACCESS_TOKEN", "ddd.eee.fff")));
		assertThrows(
				OAuth2AuthenticationException.class,
				() -> resolver.convert(duplicate).block());
	}

	@Test
	void authorizationHeaderIsNeverAcceptedAsBrowserAuthentication() {
		MockServerWebExchange exchange = exchange(
				MockServerHttpRequest.post("/api/v1/auth/logout")
						.header("Authorization", "Bearer aaa.bbb.ccc"));

		assertNull(resolver.convert(exchange).block());
	}

	private static MockServerWebExchange exchange(
			MockServerHttpRequest.BaseBuilder<?> request) {
		return MockServerWebExchange.from(request.build());
	}

	private static GatewaySecurityProperties properties() {
		return new GatewaySecurityProperties(
				"SAHHA_ACCESS_TOKEN",
				URI.create(
						"http://localhost:8081/.well-known/jwks.json"),
				"http://localhost:8081",
				"sahha-api",
				List.of("http://localhost:5173"));
	}
}
