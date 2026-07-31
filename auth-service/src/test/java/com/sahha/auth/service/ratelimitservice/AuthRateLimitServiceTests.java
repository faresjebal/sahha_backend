package com.sahha.auth.service.ratelimitservice;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sahha.auth.config.AuthRateLimitProperties;
import com.sahha.auth.exception.RateLimitExceededException;
import com.sahha.auth.security.TokenHashingService;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitServiceTests {

	private static final Instant NOW = Instant.parse("2026-07-29T20:00:00Z");

	@Mock
	private RedisAuthRateLimitStore redisStore;

	private AuthRateLimitService service;

	@BeforeEach
	void setUp() {
		lenient().when(redisStore.increment(anyString(), any(), any()))
				.thenReturn(Optional.empty());
		service = new AuthRateLimitService(
				properties(true),
				redisStore,
				new InMemoryAuthRateLimitStore(),
				new TokenHashingService());
	}

	@Test
	void localFallbackLimitsOneAddressWithoutAffectingAnother() {
		service.check(AuthRateLimitScope.LOGIN, "192.0.2.10", NOW);
		service.check(
				AuthRateLimitScope.LOGIN,
				"192.0.2.10",
				NOW.plusSeconds(1));

		RateLimitExceededException exceeded = assertThrows(
				RateLimitExceededException.class,
				() -> service.check(
						AuthRateLimitScope.LOGIN,
						"192.0.2.10",
						NOW.plusSeconds(2)));
		assertTrue(exceeded.getRetryAfter().isPositive());

		service.check(
				AuthRateLimitScope.LOGIN,
				"192.0.2.11",
				NOW.plusSeconds(2));
	}

	@Test
	void redisKeyContainsOnlyAHashOfTheNetworkAddress() {
		service.check(AuthRateLimitScope.LOGIN, "198.51.100.25", NOW);

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(redisStore).increment(
				key.capture(),
				any(),
				any());
		assertTrue(key.getValue().startsWith(
				"sahha:auth:rate-limit:v1:login:"));
		assertTrue(!key.getValue().contains("198.51.100.25"));
	}

	@Test
	void disabledRateLimitingDoesNotTouchRedis() {
		AuthRateLimitService disabled = new AuthRateLimitService(
				properties(false),
				redisStore,
				new InMemoryAuthRateLimitStore(),
				new TokenHashingService());

		disabled.check(AuthRateLimitScope.LOGIN, "192.0.2.50", NOW);

		verify(redisStore, never()).increment(anyString(), any(), any());
	}

	private static AuthRateLimitProperties properties(boolean enabled) {
		return new AuthRateLimitProperties(
				enabled,
				2,
				Duration.ofMinutes(15),
				2,
				Duration.ofHours(1),
				2,
				Duration.ofHours(1),
				2,
				Duration.ofMinutes(15),
				2,
				Duration.ofMinutes(5));
	}
}
