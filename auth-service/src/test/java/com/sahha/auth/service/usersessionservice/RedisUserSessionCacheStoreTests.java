package com.sahha.auth.service.usersessionservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.sahha.auth.config.AuthSessionCacheProperties;
import com.sahha.auth.entity.SessionStatus;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisUserSessionCacheStoreTests {

	private static final Instant NOW =
			Instant.parse("2026-07-27T12:00:00Z");
	private static final UUID SESSION_ID = UUID.randomUUID();

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private ObjectMapper objectMapper;

	private RedisUserSessionCacheStore store;

	@BeforeEach
	void setUp() {
		store = new RedisUserSessionCacheStore(
				redisTemplate,
				objectMapper,
				new AuthSessionCacheProperties(
						true,
						Duration.ofSeconds(30)));
	}

	@Test
	void connectionFailureBecomesACacheMiss() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(key()))
				.thenThrow(new RedisConnectionFailureException(
						"synthetic outage"));

		assertTrue(store.find(SESSION_ID).isEmpty());
	}

	@Test
	void malformedJsonIsDiscardedWithoutEscapingTheCacheBoundary() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(key())).thenReturn("not-json");
		when(objectMapper.readValue(
				"not-json",
				CachedUserSession.class))
				.thenThrow(new IllegalArgumentException(
						"synthetic malformed value"));

		assertTrue(store.find(SESSION_ID).isEmpty());
		verify(redisTemplate).delete(key());
	}

	@Test
	void versionedWriteUsesTheBoundedActiveTtl() {
		CachedUserSession projection = activeProjection();
		when(objectMapper.writeValueAsString(projection))
				.thenReturn("{\"status\":\"ACTIVE\"}");
		when(redisTemplate.execute(
				any(),
				anyList(),
				any(),
				any(),
				any(),
				any()))
				.thenReturn(1L);

		assertTrue(store.put(projection, NOW));
		verify(redisTemplate).execute(
				any(),
				anyList(),
				org.mockito.ArgumentMatchers.eq("30000"),
				org.mockito.ArgumentMatchers.eq("2"),
				org.mockito.ArgumentMatchers.eq("ACTIVE"),
				org.mockito.ArgumentMatchers.eq(
						"{\"status\":\"ACTIVE\"}"));
	}

	@Test
	void disabledCacheNeverContactsRedis() {
		RedisUserSessionCacheStore disabled =
				new RedisUserSessionCacheStore(
						redisTemplate,
						objectMapper,
						new AuthSessionCacheProperties(
								false,
								Duration.ofSeconds(30)));

		assertTrue(disabled.find(SESSION_ID).isEmpty());
		assertFalse(disabled.put(activeProjection(), NOW));
		disabled.evict(SESSION_ID);

		verify(redisTemplate, never()).opsForValue();
		verify(redisTemplate, never()).execute(
				any(),
				anyList(),
				any(),
				any(),
				any(),
				any());
	}

	private static CachedUserSession activeProjection() {
		return new CachedUserSession(
				CachedUserSession.CURRENT_SCHEMA_VERSION,
				SESSION_ID,
				UUID.randomUUID(),
				SessionStatus.ACTIVE,
				1,
				null,
				NOW.plus(Duration.ofDays(7)),
				NOW.plus(Duration.ofDays(30)),
				2);
	}

	private static String key() {
		return RedisUserSessionCacheStore.KEY_PREFIX + SESSION_ID;
	}
}
