package com.sahha.auth.service.usersessionservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sahha.auth.config.AuthSessionCacheProperties;
import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.repository.UserSessionRepository;

@ExtendWith(MockitoExtension.class)
class UserSessionCacheServiceTests {

	private static final Instant NOW =
			Instant.parse("2026-07-27T12:00:00Z");

	@Mock
	private UserSessionCacheStore cacheStore;

	@Mock
	private UserSessionRepository sessionRepository;

	private UserSessionCacheService cacheService;

	@BeforeEach
	void setUp() {
		cacheService = new UserSessionCacheService(
				cacheStore,
				sessionRepository,
				new AuthSessionCacheProperties(
						true,
						Duration.ofSeconds(30)));
	}

	@Test
	void validCacheHitAvoidsPostgreSql() {
		CachedUserSession cached = projection(
				CachedUserSession.CURRENT_SCHEMA_VERSION);
		when(cacheStore.find(cached.sessionId()))
				.thenReturn(Optional.of(cached));

		assertTrue(cacheService.isActive(
				cached.sessionId(),
				cached.userId(),
				cached.credentialVersion(),
				NOW));
		verify(sessionRepository, never()).findByIdWithUser(any());
	}

	@Test
	void cacheMissFallsBackToPostgreSqlAndRepopulates() {
		UserSession persisted = persistedSession();
		when(cacheStore.find(persisted.getId()))
				.thenReturn(Optional.empty());
		when(sessionRepository.findByIdWithUser(persisted.getId()))
				.thenReturn(Optional.of(persisted));
		when(cacheStore.put(any(), eq(NOW))).thenReturn(true);

		CachedUserSession result = cacheService.find(
				persisted.getId(),
				NOW)
				.orElseThrow();

		assertEquals(persisted.getId(), result.sessionId());
		assertEquals(persisted.getUser().getId(), result.userId());
		verify(cacheStore).put(result, NOW);
	}

	@Test
	void staleSchemaIsEvictedAndCannotAuthorise() {
		CachedUserSession stale = projection(1);
		when(cacheStore.find(stale.sessionId()))
				.thenReturn(Optional.of(stale));
		when(sessionRepository.findByIdWithUser(stale.sessionId()))
				.thenReturn(Optional.empty());

		assertFalse(cacheService.isActive(
				stale.sessionId(),
				stale.userId(),
				stale.credentialVersion(),
				NOW));
		verify(cacheStore).evict(stale.sessionId());
	}

	@Test
	void cacheWriteWaitsUntilTheDatabaseTransactionCommits() {
		CachedUserSession projection = projection(
				CachedUserSession.CURRENT_SCHEMA_VERSION);
		when(cacheStore.put(projection, NOW)).thenReturn(true);
		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);

		try {
			cacheService.cacheAfterCommit(projection, NOW);
			verify(cacheStore, never()).put(any(), any());

			TransactionSynchronizationManager.getSynchronizations()
					.forEach(synchronization -> synchronization.afterCommit());

			verify(cacheStore).put(projection, NOW);
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
			TransactionSynchronizationManager
					.setActualTransactionActive(false);
		}
	}

	private static UserSession persistedSession() {
		UserAccount user = UserAccount.pendingRegistration(
				"cache-owner@example.com",
				"cache-owner@example.com",
				"synthetic-password-hash",
				"Synthetic",
				"Cache",
				null);
		return UserSession.open(
				user,
				"a".repeat(64),
				"Laptop",
				"Synthetic browser",
				"192.0.2.10",
				NOW.minusSeconds(10),
				NOW.plusSeconds(60),
				NOW.plusSeconds(120));
	}

	private static CachedUserSession projection(int schemaVersion) {
		return new CachedUserSession(
				schemaVersion,
				UUID.randomUUID(),
				UUID.randomUUID(),
				SessionStatus.ACTIVE,
				1,
				null,
				java.util.List.of(),
				NOW.plusSeconds(60),
				NOW.plusSeconds(120),
				1);
	}
}
