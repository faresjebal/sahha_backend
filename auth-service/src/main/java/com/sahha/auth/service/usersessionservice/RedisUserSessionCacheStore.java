package com.sahha.auth.service.usersessionservice;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.sahha.auth.config.AuthSessionCacheProperties;

import tools.jackson.databind.ObjectMapper;

@Component
public class RedisUserSessionCacheStore implements UserSessionCacheStore {

	public static final String KEY_PREFIX = "sahha:auth:session:v1:";

	private static final Logger LOGGER =
			LoggerFactory.getLogger(RedisUserSessionCacheStore.class);
	private static final DefaultRedisScript<Long> VERSIONED_WRITE =
			new DefaultRedisScript<>(
					"""
					local currentJson = redis.call('GET', KEYS[1])
					if currentJson then
					  local decoded, current = pcall(cjson.decode, currentJson)
					  if decoded and current then
					    local currentVersion = tonumber(current.version) or -1
					    local proposedVersion = tonumber(ARGV[2])
					    if currentVersion > proposedVersion then
					      return 0
					    end
					    if currentVersion == proposedVersion
					       and current.status ~= 'ACTIVE'
					       and ARGV[3] == 'ACTIVE' then
					      return 0
					    end
					  end
					end
					redis.call('SET', KEYS[1], ARGV[4], 'PX', ARGV[1])
					return 1
					""",
					Long.class);

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final AuthSessionCacheProperties properties;
	private final AtomicBoolean outageWarningLogged = new AtomicBoolean();

	public RedisUserSessionCacheStore(
			StringRedisTemplate redisTemplate,
			ObjectMapper objectMapper,
			AuthSessionCacheProperties properties) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Override
	public Optional<CachedUserSession> find(UUID sessionId) {
		if (!properties.enabled()) {
			return Optional.empty();
		}

		String cachedJson;
		try {
			cachedJson = redisTemplate.opsForValue().get(key(sessionId));
			markAvailable();
		}
		catch (RuntimeException unavailable) {
			logUnavailable("read", unavailable);
			return Optional.empty();
		}
		if (cachedJson == null) {
			return Optional.empty();
		}

		try {
			return Optional.of(objectMapper.readValue(
					cachedJson,
					CachedUserSession.class));
		}
		catch (RuntimeException malformed) {
			LOGGER.warn(
					"Discarding malformed Redis session cache entry exception={}",
					malformed.getClass().getSimpleName());
			evict(sessionId);
			return Optional.empty();
		}
	}

	@Override
	public boolean put(
			CachedUserSession session,
			Instant observedAt) {
		if (!properties.enabled()) {
			return false;
		}

		Duration timeToLive = session.timeToLive(
				observedAt,
				properties.maximumActiveTtl());
		if (timeToLive.isZero() || timeToLive.isNegative()) {
			evict(session.sessionId());
			return false;
		}

		try {
			String cachedJson = objectMapper.writeValueAsString(session);
			Long result = redisTemplate.execute(
					VERSIONED_WRITE,
					List.of(key(session.sessionId())),
					Long.toString(Math.max(1, timeToLive.toMillis())),
					Long.toString(session.version()),
					session.status().name(),
					cachedJson);
			markAvailable();
			return Long.valueOf(1L).equals(result);
		}
		catch (RuntimeException unavailable) {
			logUnavailable("write", unavailable);
			return false;
		}
	}

	@Override
	public void evict(UUID sessionId) {
		if (!properties.enabled()) {
			return;
		}
		try {
			redisTemplate.delete(key(sessionId));
			markAvailable();
		}
		catch (RuntimeException unavailable) {
			logUnavailable("eviction", unavailable);
		}
	}

	private void logUnavailable(String operation, RuntimeException failure) {
		if (outageWarningLogged.compareAndSet(false, true)) {
			LOGGER.warn(
					"Redis session cache {} failed; PostgreSQL fallback remains active exception={}",
					operation,
					failure.getClass().getSimpleName());
		}
	}

	private void markAvailable() {
		if (outageWarningLogged.compareAndSet(true, false)) {
			LOGGER.info("Redis session cache connection recovered");
		}
	}

	private static String key(UUID sessionId) {
		if (sessionId == null) {
			throw new IllegalArgumentException(
					"sessionId must not be null");
		}
		return KEY_PREFIX + sessionId;
	}
}
