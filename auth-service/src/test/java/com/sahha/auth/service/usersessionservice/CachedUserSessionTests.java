package com.sahha.auth.service.usersessionservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sahha.auth.entity.SessionStatus;

class CachedUserSessionTests {

	private static final Instant NOW =
			Instant.parse("2026-07-27T12:00:00Z");
	private static final UUID SESSION_ID = UUID.randomUUID();
	private static final UUID USER_ID = UUID.randomUUID();

	@Test
	void activeTtlIsCappedByTheConfiguredDecisionWindow() {
		CachedUserSession session = projection(
				SessionStatus.ACTIVE,
				NOW.plus(Duration.ofDays(7)),
				NOW.plus(Duration.ofDays(30)));

		assertEquals(
				Duration.ofSeconds(30),
				session.timeToLive(NOW, Duration.ofSeconds(30)));
	}

	@Test
	void activeTtlNeverOutlivesIdleOrAbsoluteExpiration() {
		CachedUserSession idleFirst = projection(
				SessionStatus.ACTIVE,
				NOW.plusSeconds(8),
				NOW.plusSeconds(20));
		CachedUserSession absoluteFirst = projection(
				SessionStatus.ACTIVE,
				NOW.plusSeconds(8),
				NOW.plusSeconds(8));

		assertEquals(
				Duration.ofSeconds(8),
				idleFirst.timeToLive(NOW, Duration.ofSeconds(30)));
		assertEquals(
				Duration.ofSeconds(8),
				absoluteFirst.timeToLive(NOW, Duration.ofSeconds(30)));
		assertEquals(
				Duration.ZERO,
				idleFirst.timeToLive(
						NOW.plusSeconds(8),
						Duration.ofSeconds(30)));
	}

	@Test
	void tombstoneSurvivesUntilAbsoluteExpiration() {
		CachedUserSession revoked = projection(
				SessionStatus.REVOKED,
				NOW.minusSeconds(1),
				NOW.plus(Duration.ofHours(2)));

		assertEquals(
				Duration.ofHours(2),
				revoked.timeToLive(NOW, Duration.ofSeconds(30)));
		assertTrue(revoked.isTombstone());
	}

	@Test
	void activeDecisionChecksOwnerCredentialsStatusAndExpirations() {
		CachedUserSession active = projection(
				SessionStatus.ACTIVE,
				NOW.plusSeconds(20),
				NOW.plusSeconds(60));

		assertTrue(active.isActiveFor(USER_ID, 4, NOW));
		assertFalse(active.isActiveFor(UUID.randomUUID(), 4, NOW));
		assertFalse(active.isActiveFor(USER_ID, 5, NOW));
		assertFalse(active.isActiveFor(
				USER_ID,
				4,
				NOW.plusSeconds(20)));
		assertFalse(projection(
				SessionStatus.COMPROMISED,
				NOW.plusSeconds(20),
				NOW.plusSeconds(60))
				.isActiveFor(USER_ID, 4, NOW));
	}

	private static CachedUserSession projection(
			SessionStatus status,
			Instant idleExpiresAt,
			Instant absoluteExpiresAt) {
		return new CachedUserSession(
				CachedUserSession.CURRENT_SCHEMA_VERSION,
				SESSION_ID,
				USER_ID,
				status,
				4,
				null,
				idleExpiresAt,
				absoluteExpiresAt,
				2);
	}
}
