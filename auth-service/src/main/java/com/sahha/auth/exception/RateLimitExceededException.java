package com.sahha.auth.exception;

import java.time.Duration;
import java.util.Objects;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

	private final Duration retryAfter;

	public RateLimitExceededException(Duration retryAfter) {
		super("authentication request rate limit exceeded");
		Duration requiredRetryAfter = Objects.requireNonNull(
				retryAfter,
				"retryAfter must not be null");
		this.retryAfter = requiredRetryAfter.isNegative()
				|| requiredRetryAfter.isZero()
						? Duration.ofSeconds(1)
						: requiredRetryAfter;
	}
}
