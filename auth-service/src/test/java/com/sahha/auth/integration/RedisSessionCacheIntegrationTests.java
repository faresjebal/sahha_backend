package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.service.useraccountservice.AccountRegistrationService;
import com.sahha.auth.service.useraccountservice.AccountVerificationService;
import com.sahha.auth.service.useraccountservice.PendingRegistrationResult;
import com.sahha.auth.service.usersessionservice.CachedUserSession;
import com.sahha.auth.service.usersessionservice.IssuedSessionCredentials;
import com.sahha.auth.service.usersessionservice.RedisUserSessionCacheStore;
import com.sahha.auth.service.usersessionservice.UserSessionCacheService;
import com.sahha.auth.service.usersessionservice.UserSessionCacheStore;
import com.sahha.auth.service.usersessionservice.UserSessionService;

@SpringBootTest(properties = {
		"sahha.auth.session-cache.enabled=true",
		"sahha.auth.session-cache.maximum-active-ttl=PT30S"
})
class RedisSessionCacheIntegrationTests {

	private static final String REDIS_HOST =
			environment("AUTH_TEST_REDIS_HOST", "localhost");
	private static final int REDIS_PORT = Integer.parseInt(
			environment("AUTH_TEST_REDIS_PORT", "6379"));
	private static final Instant BASE_TIME =
			Instant.parse("2026-07-27T12:00:00Z");
	private static final String PASSWORD =
			"synthetic redis cache passphrase";

	@BeforeAll
	static void requireExternalRedis() throws IOException {
		try (Socket socket = new Socket()) {
			socket.connect(
					new InetSocketAddress(REDIS_HOST, REDIS_PORT),
					1_000);
		}
	}

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", () -> REDIS_HOST);
		registry.add("spring.data.redis.port", () -> REDIS_PORT);
	}

	@Autowired
	private UserSessionCacheStore cacheStore;

	@Autowired
	private UserSessionCacheService cacheService;

	@Autowired
	private UserSessionService sessionService;

	@Autowired
	private AccountRegistrationService registrationService;

	@Autowired
	private AccountVerificationService verificationService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@BeforeEach
	void clearSessionKeys() {
		Set<String> keys = redisTemplate.keys(
				RedisUserSessionCacheStore.KEY_PREFIX + "*");
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}

	@AfterEach
	void removeSessionKeys() {
		clearSessionKeys();
	}

	@Test
	void activeProjectionRoundTripsWithBoundedTtlAndNoSensitiveFields() {
		Instant now = Instant.now();
		UUID sessionId = UUID.randomUUID();
		CachedUserSession active = projection(
				sessionId,
				SessionStatus.ACTIVE,
				1,
				now.plus(Duration.ofDays(7)),
				now.plus(Duration.ofDays(30)));

		assertTrue(cacheStore.put(active, now));
		assertEquals(active, cacheStore.find(sessionId).orElseThrow());

		String key = RedisUserSessionCacheStore.KEY_PREFIX + sessionId;
		String cachedJson = redisTemplate.opsForValue().get(key);
		assertNotNull(cachedJson);
		Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
		assertNotNull(ttl);
		assertTrue(ttl > 0 && ttl <= 30_000);
		assertFalse(cachedJson.contains("password"));
		assertFalse(cachedJson.contains("token"));
		assertFalse(cachedJson.contains("email"));
		assertFalse(cachedJson.contains("phone"));
		assertFalse(cachedJson.contains("device"));
		assertFalse(cachedJson.contains("userAgent"));
		assertFalse(cachedJson.contains("\"ip\""));
	}

	@Test
	void tombstoneRejectsAnOlderActiveWriteAndUsesAbsoluteTtl() {
		Instant now = Instant.now();
		UUID sessionId = UUID.randomUUID();
		Instant absoluteExpiration = now.plus(Duration.ofMinutes(10));
		CachedUserSession active = projection(
				sessionId,
				SessionStatus.ACTIVE,
				1,
				now.plus(Duration.ofMinutes(5)),
				absoluteExpiration);
		CachedUserSession compromised = projection(
				sessionId,
				SessionStatus.COMPROMISED,
				2,
				now.plus(Duration.ofMinutes(5)),
				absoluteExpiration);

		assertTrue(cacheStore.put(active, now));
		assertTrue(cacheStore.put(compromised, now));
		assertFalse(cacheStore.put(active, now));
		assertEquals(
				SessionStatus.COMPROMISED,
				cacheStore.find(sessionId).orElseThrow().status());

		Long ttl = redisTemplate.getExpire(
				RedisUserSessionCacheStore.KEY_PREFIX + sessionId,
				TimeUnit.MILLISECONDS);
		assertNotNull(ttl);
		assertTrue(ttl > 30_000 && ttl <= Duration.ofMinutes(10).toMillis());
	}

	@Test
	void loginLogoutAndCacheMissRemainBackedByPostgreSql() {
		VerifiedAccount account = verifiedAccount();
		Instant loggedInAt = BASE_TIME.plusSeconds(120);
		IssuedSessionCredentials credentials = sessionService.login(
				account.email(),
				PASSWORD,
				"redis-integration-device",
				"Laptop",
				"Synthetic browser",
				"192.0.2.80",
				loggedInAt)
				.orElseThrow();

		CachedUserSession initiallyCached = cacheStore.find(
				credentials.getSessionId())
				.orElseThrow();
		assertEquals(SessionStatus.ACTIVE, initiallyCached.status());

		cacheStore.evict(credentials.getSessionId());
		assertTrue(cacheStore.find(credentials.getSessionId()).isEmpty());
		assertTrue(cacheService.isActive(
				credentials.getSessionId(),
				account.userId(),
				initiallyCached.credentialVersion(),
				loggedInAt.plusSeconds(1)));
		assertTrue(cacheStore.find(credentials.getSessionId()).isPresent());

		assertTrue(sessionService.logoutCurrent(
				account.userId(),
				credentials.getSessionId(),
				loggedInAt.plusSeconds(2)));
		assertEquals(
				SessionStatus.REVOKED,
				cacheStore.find(credentials.getSessionId())
						.orElseThrow()
						.status());

		cacheStore.evict(credentials.getSessionId());
		assertFalse(cacheService.isActive(
				credentials.getSessionId(),
				account.userId(),
				initiallyCached.credentialVersion(),
				loggedInAt.plusSeconds(3)));
		assertEquals(
				SessionStatus.REVOKED,
				cacheStore.find(credentials.getSessionId())
						.orElseThrow()
						.status());
	}

	private VerifiedAccount verifiedAccount() {
		String email = "redis-cache-" + UUID.randomUUID() + "@example.com";
		PendingRegistrationResult registration = registrationService.register(
				email,
				PASSWORD,
				"Synthetic",
				"Redis",
				null,
				BASE_TIME);
		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				BASE_TIME.plusSeconds(1));
		return new VerifiedAccount(registration.getUserId(), email);
	}

	private static CachedUserSession projection(
			UUID sessionId,
			SessionStatus status,
			long version,
			Instant idleExpiresAt,
			Instant absoluteExpiresAt) {
		return new CachedUserSession(
				CachedUserSession.CURRENT_SCHEMA_VERSION,
				sessionId,
				UUID.randomUUID(),
				status,
				1,
				null,
				idleExpiresAt,
				absoluteExpiresAt,
				version);
	}

	private record VerifiedAccount(UUID userId, String email) {
	}

	private static String environment(String name, String defaultValue) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? defaultValue : value;
	}
}
