package com.sahha.auth.service.ratelimitservice;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.sahha.auth.config.AuthRateLimitProperties;
import com.sahha.auth.exception.RateLimitExceededException;
import com.sahha.auth.security.TokenHashingService;

@Service
public class AuthRateLimitService {

	private static final String KEY_PREFIX = "sahha:auth:rate-limit:v1:";

	private final AuthRateLimitProperties properties;
	private final RedisAuthRateLimitStore redisStore;
	private final InMemoryAuthRateLimitStore localStore;
	private final TokenHashingService hashingService;

	public AuthRateLimitService(
			AuthRateLimitProperties properties,
			RedisAuthRateLimitStore redisStore,
			InMemoryAuthRateLimitStore localStore,
			TokenHashingService hashingService) {
		this.properties = properties;
		this.redisStore = redisStore;
		this.localStore = localStore;
		this.hashingService = hashingService;
	}

	public void check(
			AuthRateLimitScope scope,
			String clientAddress,
			Instant observedAt) {
		if (!properties.enabled()) {
			return;
		}
		AuthRateLimitScope requiredScope = Objects.requireNonNull(
				scope,
				"scope must not be null");
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		String addressHash = hashingService.hash(
				requireAddress(clientAddress));
		AuthRateLimitPolicy policy = policy(requiredScope);
		String key = KEY_PREFIX
				+ requiredScope.name().toLowerCase(java.util.Locale.ROOT)
				+ ":"
				+ addressHash;
		RateLimitCounter counter = redisStore
				.increment(key, policy.window(), requiredObservedAt)
				.orElseGet(() -> localStore.increment(
						key,
						policy.window(),
						requiredObservedAt));
		if (counter.attempts() > policy.maximumAttempts()) {
			Duration retryAfter = Duration.between(
					requiredObservedAt,
					counter.resetsAt());
			throw new RateLimitExceededException(retryAfter);
		}
	}

	private AuthRateLimitPolicy policy(AuthRateLimitScope scope) {
		return switch (scope) {
			case LOGIN -> new AuthRateLimitPolicy(
					properties.loginMaximumAttempts(),
					properties.loginWindow());
			case REGISTRATION -> new AuthRateLimitPolicy(
					properties.registrationMaximumAttempts(),
					properties.registrationWindow());
			case EMAIL_VERIFICATION_REQUEST, PASSWORD_RESET_REQUEST ->
					new AuthRateLimitPolicy(
							properties.emailRequestMaximumAttempts(),
							properties.emailRequestWindow());
			case EMAIL_VERIFICATION_CONFIRMATION,
					PASSWORD_RESET_CONFIRMATION ->
					new AuthRateLimitPolicy(
							properties.tokenConfirmationMaximumAttempts(),
							properties.tokenConfirmationWindow());
			case REFRESH -> new AuthRateLimitPolicy(
					properties.refreshMaximumAttempts(),
					properties.refreshWindow());
		};
	}

	private static String requireAddress(String clientAddress) {
		if (clientAddress == null || clientAddress.isBlank()) {
			return "unavailable";
		}
		String stripped = clientAddress.strip();
		return stripped.length() <= 45
				? stripped
				: stripped.substring(0, 45);
	}
}
