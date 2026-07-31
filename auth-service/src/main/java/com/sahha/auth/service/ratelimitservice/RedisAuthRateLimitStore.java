package com.sahha.auth.service.ratelimitservice;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisAuthRateLimitStore {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(RedisAuthRateLimitStore.class);
	private static final DefaultRedisScript<List> INCREMENT =
			new DefaultRedisScript<>(
					"""
					local attempts = redis.call('INCR', KEYS[1])
					if attempts == 1 then
					  redis.call('PEXPIRE', KEYS[1], ARGV[1])
					end
					return {attempts, redis.call('PTTL', KEYS[1])}
					""",
					List.class);

	private final StringRedisTemplate redisTemplate;
	private final AtomicBoolean outageWarningLogged = new AtomicBoolean();

	RedisAuthRateLimitStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	Optional<RateLimitCounter> increment(
			String key,
			Duration window,
			Instant observedAt) {
		try {
			List<?> result = redisTemplate.execute(
					INCREMENT,
					List.of(key),
					Long.toString(Math.max(1, window.toMillis())));
			if (result == null || result.size() != 2) {
				return Optional.empty();
			}
			long attempts = number(result.get(0));
			long ttlMillis = Math.max(1, number(result.get(1)));
			markAvailable();
			return Optional.of(new RateLimitCounter(
					attempts,
					observedAt.plusMillis(ttlMillis)));
		}
		catch (RuntimeException unavailable) {
			if (outageWarningLogged.compareAndSet(false, true)) {
				LOGGER.warn(
						"Redis authentication throttling is unavailable; local fallback is active exception={}",
						unavailable.getClass().getSimpleName());
			}
			return Optional.empty();
		}
	}

	private static long number(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(String.valueOf(value));
	}

	private void markAvailable() {
		if (outageWarningLogged.compareAndSet(true, false)) {
			LOGGER.info("Redis authentication throttling connection recovered");
		}
	}
}
