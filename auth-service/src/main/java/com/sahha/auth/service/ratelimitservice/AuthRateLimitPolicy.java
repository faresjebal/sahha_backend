package com.sahha.auth.service.ratelimitservice;

import java.time.Duration;

record AuthRateLimitPolicy(int maximumAttempts, Duration window) {
}
