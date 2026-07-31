package com.sahha.auth.service.browsersessionservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.sahha.auth.config.AuthCookieProperties;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.security.SecureTokenGenerator;
import com.sahha.auth.service.accesstokenservice.IssuedAccessToken;
import com.sahha.auth.service.refreshtokenservice.IssuedRefreshToken;
import com.sahha.auth.service.usersessionservice.IssuedBrowserSession;
import com.sahha.auth.service.usersessionservice.IssuedSessionCredentials;

class BrowserSessionCookieServiceTests {

	private static final Instant NOW =
			Instant.parse("2026-07-28T12:00:00Z");

	@Test
	void writesScopedHttpOnlyCookiesWithoutSecureOnlyInLocalMode() {
		BrowserSessionCookieService service = service(false);
		MockHttpServletResponse response = new MockHttpServletResponse();

		service.writeAuthenticatedSession(
				response,
				browserSession(),
				"local-device-identifier-abcdefghijklmnopqrstuvwxyz");

		List<String> headers = response.getHeaders(
				HttpHeaders.SET_COOKIE);
		assertEquals(3, headers.size());
		assertTrue(cookie(headers, "SAHHA_ACCESS_TOKEN")
				.contains("Path=/"));
		assertTrue(cookie(headers, "SAHHA_ACCESS_TOKEN")
				.contains("HttpOnly"));
		assertTrue(cookie(headers, "SAHHA_ACCESS_TOKEN")
				.contains("SameSite=Lax"));
		assertFalse(cookie(headers, "SAHHA_ACCESS_TOKEN")
				.contains("Secure"));
		assertTrue(cookie(headers, "SAHHA_REFRESH_TOKEN")
				.contains("Path=/api/v1/auth"));
		assertTrue(cookie(headers, "SAHHA_REFRESH_TOKEN")
				.contains("HttpOnly"));
		assertTrue(cookie(headers, "SAHHA_DEVICE_ID")
				.contains("Max-Age=31536000"));
		assertEquals(
				"no-store, no-cache, max-age=0",
				response.getHeader(HttpHeaders.CACHE_CONTROL));
	}

	@Test
	void clearingCredentialsUsesTheOriginalCookieScopes() {
		BrowserSessionCookieService service = service(false);
		MockHttpServletResponse response = new MockHttpServletResponse();

		service.clearAuthentication(response);

		List<String> headers = response.getHeaders(
				HttpHeaders.SET_COOKIE);
		assertEquals(2, headers.size());
		assertTrue(cookie(headers, "SAHHA_ACCESS_TOKEN")
				.contains("Max-Age=0"));
		assertTrue(cookie(headers, "SAHHA_ACCESS_TOKEN")
				.contains("Path=/"));
		assertTrue(cookie(headers, "SAHHA_REFRESH_TOKEN")
				.contains("Max-Age=0"));
		assertTrue(cookie(headers, "SAHHA_REFRESH_TOKEN")
				.contains("Path=/api/v1/auth"));
	}

	@Test
	void invalidOrMissingDeviceCookieCreatesAHighEntropyIdentifier() {
		BrowserSessionCookieService service = service(false);
		MockHttpServletRequest request = new MockHttpServletRequest();

		String deviceId = service.resolveOrCreateDeviceId(request);

		assertTrue(deviceId.matches("^[A-Za-z0-9_-]{43}$"));
	}

	@Test
	void productionModeMarksEveryAuthenticationCookieSecure() {
		BrowserSessionCookieService service = service(true);
		MockHttpServletResponse response = new MockHttpServletResponse();

		service.writeAuthenticatedSession(
				response,
				browserSession(),
				"local-device-identifier-abcdefghijklmnopqrstuvwxyz");

		for (String header : response.getHeaders(HttpHeaders.SET_COOKIE)) {
			assertTrue(header.contains("Secure"));
			assertTrue(header.contains("HttpOnly"));
		}
	}

	private static BrowserSessionCookieService service(boolean secure) {
		AuthCookieProperties properties = new AuthCookieProperties(
				"SAHHA_ACCESS_TOKEN",
				"SAHHA_REFRESH_TOKEN",
				"SAHHA_DEVICE_ID",
				"XSRF-TOKEN",
				"X-XSRF-TOKEN",
				"/",
				"/api/v1/auth",
				"Lax",
				secure,
				Duration.ofDays(365));
		CookieCsrfTokenRepository csrf =
				CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrf.setCookieName(properties.csrfTokenName());
		csrf.setHeaderName(properties.csrfHeaderName());
		csrf.setCookiePath(properties.accessPath());
		return new BrowserSessionCookieService(
				properties,
				new SecureTokenGenerator(new SecureRandom()),
				csrf,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static IssuedBrowserSession browserSession() {
		UserAccount user = UserAccount.pendingRegistration(
				"synthetic.cookies@example.com",
				"synthetic.cookies@example.com",
				"bcrypt-hash",
				"Synthetic",
				"Cookie",
				null,
				NOW.minusSeconds(60));
		user.verifyEmail(NOW.minusSeconds(59));
		UserSession session = UserSession.open(
				user,
				null,
				"1".repeat(64),
				"Test device",
				null,
				null,
				NOW.minusSeconds(1),
				NOW.plus(Duration.ofDays(7)),
				NOW.plus(Duration.ofDays(30)));
		IssuedRefreshToken refresh = new IssuedRefreshToken(
				java.util.UUID.randomUUID(),
				session.getId(),
				"refresh-cookie-secret",
				NOW.plus(Duration.ofDays(7)));
		IssuedSessionCredentials credentials =
				IssuedSessionCredentials.from(session, refresh);
		return new IssuedBrowserSession(
				credentials,
				new IssuedAccessToken(
						"header.payload.signature",
						NOW,
						NOW.plusSeconds(600)),
				List.of("PLATFORM_ADMIN"));
	}

	private static String cookie(List<String> headers, String name) {
		return headers.stream()
				.filter(value -> value.startsWith(name + "="))
				.findFirst()
				.orElseThrow();
	}
}
