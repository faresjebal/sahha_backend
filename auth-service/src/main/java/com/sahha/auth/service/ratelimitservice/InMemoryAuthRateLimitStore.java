package com.sahha.auth.service.ratelimitservice;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
class InMemoryAuthRateLimitStore {

	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	RateLimitCounter increment(
			String key,
			Duration window,
			Instant observedAt) {
		if (entries.size() > CLEANUP_THRESHOLD) {
			entries.entrySet().removeIf(
					entry -> !observedAt.isBefore(entry.getValue().resetsAt()));
		}
		Entry entry = entries.compute(
				key,
				(ignored, current) -> current == null
						|| !observedAt.isBefore(current.resetsAt())
								? new Entry(1, observedAt.plus(window))
								: new Entry(
										Math.addExact(current.attempts(), 1),
										current.resetsAt()));
		return new RateLimitCounter(entry.attempts(), entry.resetsAt());
	}

	private record Entry(long attempts, Instant resetsAt) {
	}
}
