package com.sahha.auth.service.ratelimitservice;

import java.time.Instant;

record RateLimitCounter(long attempts, Instant resetsAt) {
}
