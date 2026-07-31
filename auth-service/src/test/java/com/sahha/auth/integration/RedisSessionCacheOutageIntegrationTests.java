package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.service.useraccountservice.AccountRegistrationService;
import com.sahha.auth.service.useraccountservice.AccountVerificationService;
import com.sahha.auth.service.useraccountservice.PendingRegistrationResult;
import com.sahha.auth.service.usersessionservice.CachedUserSession;
import com.sahha.auth.service.usersessionservice.IssuedSessionCredentials;
import com.sahha.auth.service.usersessionservice.UserSessionCacheService;
import com.sahha.auth.service.usersessionservice.UserSessionCacheStore;
import com.sahha.auth.service.usersessionservice.UserSessionService;

@SpringBootTest(properties = {
		"sahha.auth.session-cache.enabled=true",
		"spring.data.redis.connect-timeout=200ms",
		"spring.data.redis.timeout=200ms"
})
class RedisSessionCacheOutageIntegrationTests {

	private static final int UNAVAILABLE_REDIS_PORT =
			findUnavailableLocalPort();
	private static final Instant BASE_TIME =
			Instant.parse("2026-07-27T14:00:00Z");
	private static final String PASSWORD =
			"synthetic redis outage passphrase";

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", () -> "127.0.0.1");
		registry.add(
				"spring.data.redis.port",
				() -> UNAVAILABLE_REDIS_PORT);
	}

	@Autowired
	private UserSessionService sessionService;

	@Autowired
	private UserSessionCacheService cacheService;

	@Autowired
	private UserSessionCacheStore cacheStore;

	@Autowired
	private AccountRegistrationService registrationService;

	@Autowired
	private AccountVerificationService verificationService;

	@Test
	void redisOutageFallsBackToTheAuthoritativePostgreSqlSession() {
		String email = "redis-outage-" + UUID.randomUUID() + "@example.com";
		PendingRegistrationResult registration = registrationService.register(
				email,
				PASSWORD,
				"Synthetic",
				"Outage",
				null,
				BASE_TIME);
		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				BASE_TIME.plusSeconds(1));
		IssuedSessionCredentials credentials = sessionService.login(
				email,
				PASSWORD,
				"redis-outage-device",
				"Laptop",
				"Synthetic browser",
				"192.0.2.90",
				BASE_TIME.plusSeconds(120))
				.orElseThrow();
		assertTrue(cacheStore.find(credentials.getSessionId()).isEmpty());

		CachedUserSession fallback = cacheService.find(
				credentials.getSessionId(),
				BASE_TIME.plusSeconds(121))
				.orElseThrow();
		assertEquals(SessionStatus.ACTIVE, fallback.status());
		assertTrue(fallback.isActiveFor(
				registration.getUserId(),
				credentials.getCredentialVersion(),
				BASE_TIME.plusSeconds(121)));
		assertFalse(cacheStore.find(credentials.getSessionId()).isPresent());
	}

	private static int findUnavailableLocalPort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			socket.setReuseAddress(false);
			return socket.getLocalPort();
		}
		catch (IOException exception) {
			throw new IllegalStateException(
					"Could not reserve an unavailable Redis test port",
					exception);
		}
	}
}
