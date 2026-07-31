package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionCacheStore {

	Optional<CachedUserSession> find(UUID sessionId);

	boolean put(CachedUserSession session, Instant observedAt);

	void evict(UUID sessionId);
}
