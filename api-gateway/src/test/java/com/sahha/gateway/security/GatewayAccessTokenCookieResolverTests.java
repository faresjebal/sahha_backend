package com.sahha.gateway.security;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
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
		MockHttpServletRequest request =
				new MockHttpServletRequest("POST", "/api/v1/auth/logout");
		request.setCookies(new Cookie("SAHHA_ACCESS_TOKEN", "aaa.bbb.ccc"));

		assertEquals("aaa.bbb.ccc", resolver.resolve(request));
	}

	@Test
	void publicRefreshIgnoresAnExpiredOrMalformedAccessCookie() {
		MockHttpServletRequest request =
				new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
		request.setCookies(new Cookie("SAHHA_ACCESS_TOKEN", "not-a-jwt"));

		assertNull(resolver.resolve(request));
	}

	@Test
	void protectedRequestRejectsMalformedAndDuplicateCookies() {
		MockHttpServletRequest malformed =
				new MockHttpServletRequest("POST", "/api/v1/auth/logout");
		malformed.setCookies(
				new Cookie("SAHHA_ACCESS_TOKEN", "not-a-jwt"));
		assertThrows(
				OAuth2AuthenticationException.class,
				() -> resolver.resolve(malformed));

		MockHttpServletRequest duplicate =
				new MockHttpServletRequest("POST", "/api/v1/auth/logout");
		duplicate.setCookies(
				new Cookie("SAHHA_ACCESS_TOKEN", "aaa.bbb.ccc"),
				new Cookie("SAHHA_ACCESS_TOKEN", "ddd.eee.fff"));
		assertThrows(
				OAuth2AuthenticationException.class,
				() -> resolver.resolve(duplicate));
	}

	@Test
	void authorizationHeaderIsNeverAcceptedAsBrowserAuthentication() {
		MockHttpServletRequest request =
				new MockHttpServletRequest("POST", "/api/v1/auth/logout");
		request.addHeader("Authorization", "Bearer aaa.bbb.ccc");

		assertNull(resolver.resolve(request));
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
