package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sahha.auth.config.AuthSessionCacheProperties;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.repository.UserSessionRepository;

@Service
public class UserSessionCacheService {

	private final UserSessionCacheStore cacheStore;
	private final UserSessionRepository sessionRepository;
	private final AuthSessionCacheProperties properties;

	public UserSessionCacheService(
			UserSessionCacheStore cacheStore,
			UserSessionRepository sessionRepository,
			AuthSessionCacheProperties properties) {
		this.cacheStore = cacheStore;
		this.sessionRepository = sessionRepository;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public Optional<CachedUserSession> find(
			UUID sessionId,
			Instant observedAt) {
		UUID requiredSessionId = Objects.requireNonNull(
				sessionId,
				"sessionId must not be null");
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");

		Optional<CachedUserSession> cached = cacheStore.find(
				requiredSessionId);
		if (cached.isPresent()) {
			CachedUserSession projection = cached.orElseThrow();
			if (projection.isCurrentSchema()
					&& requiredSessionId.equals(projection.sessionId())
					&& !projection.timeToLive(
							requiredObservedAt,
							properties.maximumActiveTtl())
							.isZero()) {
				return cached;
			}
			cacheStore.evict(requiredSessionId);
		}

		Optional<UserSession> persisted =
				sessionRepository.findByIdWithUser(requiredSessionId);
		if (persisted.isEmpty()) {
			return Optional.empty();
		}

		CachedUserSession authoritative = CachedUserSession.from(
				persisted.orElseThrow());
		cacheAfterCommit(authoritative, requiredObservedAt);
		return Optional.of(authoritative);
	}

	@Transactional(readOnly = true)
	public boolean isActive(
			UUID sessionId,
			UUID expectedUserId,
			int expectedCredentialVersion,
			Instant observedAt) {
		Objects.requireNonNull(
				expectedUserId,
				"expectedUserId must not be null");
		return find(sessionId, observedAt)
				.map(session -> session.isActiveFor(
						expectedUserId,
						expectedCredentialVersion,
						observedAt))
				.orElse(false);
	}

	@Transactional(readOnly = true)
	public boolean isActiveForContext(
			UUID sessionId,
			UUID expectedUserId,
			int expectedCredentialVersion,
			UUID expectedActiveOrganisationId,
			List<String> expectedActiveOrganisationRoles,
			Instant observedAt) {
		Objects.requireNonNull(
				expectedUserId,
				"expectedUserId must not be null");
		Objects.requireNonNull(
				expectedActiveOrganisationRoles,
				"expectedActiveOrganisationRoles must not be null");
		return find(sessionId, observedAt)
				.map(session -> session.isActiveForContext(
						expectedUserId,
						expectedCredentialVersion,
						expectedActiveOrganisationId,
						expectedActiveOrganisationRoles,
						observedAt))
				.orElse(false);
	}

	public void cacheAfterCommit(
			UserSession session,
			Instant observedAt) {
		cacheAfterCommit(
				CachedUserSession.from(session),
				observedAt);
	}

	void cacheAfterCommit(
			CachedUserSession session,
			Instant observedAt) {
		CachedUserSession requiredSession = Objects.requireNonNull(
				session,
				"session must not be null");
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		Runnable cacheWrite = () -> cacheStore.put(
				requiredSession,
				requiredObservedAt);

		if (TransactionSynchronizationManager
				.isActualTransactionActive()
				&& TransactionSynchronizationManager
						.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(
					new TransactionSynchronization() {
						@Override
						public void afterCommit() {
							cacheWrite.run();
						}
					});
			return;
		}
		cacheWrite.run();
	}
}
