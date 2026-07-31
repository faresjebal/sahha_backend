package com.sahha.auth.service.usersessionservice;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.entity.UserSession;

public record CachedUserSession(
		int schemaVersion,
		UUID sessionId,
		UUID userId,
		SessionStatus status,
		int credentialVersion,
		UUID activeOrganisationId,
		Instant idleExpiresAt,
		Instant absoluteExpiresAt,
		long version) {

	public static final int CURRENT_SCHEMA_VERSION = 1;

	public CachedUserSession {
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(
				idleExpiresAt,
				"idleExpiresAt must not be null");
		Objects.requireNonNull(
				absoluteExpiresAt,
				"absoluteExpiresAt must not be null");
		if (schemaVersion < 1) {
			throw new IllegalArgumentException(
					"schemaVersion must be positive");
		}
		if (credentialVersion < 1) {
			throw new IllegalArgumentException(
					"credentialVersion must be positive");
		}
		if (version < 0) {
			throw new IllegalArgumentException(
					"version must not be negative");
		}
		if (idleExpiresAt.isAfter(absoluteExpiresAt)) {
			throw new IllegalArgumentException(
					"idleExpiresAt cannot exceed absoluteExpiresAt");
		}
	}

	public static CachedUserSession from(UserSession session) {
		UserSession requiredSession = Objects.requireNonNull(
				session,
				"session must not be null");
		return new CachedUserSession(
				CURRENT_SCHEMA_VERSION,
				requiredSession.getId(),
				requiredSession.getUser().getId(),
				requiredSession.getStatus(),
				requiredSession.getCredentialVersionAtCreation(),
				requiredSession.getActiveOrganisationId(),
				requiredSession.getIdleExpiresAt(),
				requiredSession.getAbsoluteExpiresAt(),
				requiredSession.getVersion());
	}

	public boolean isCurrentSchema() {
		return schemaVersion == CURRENT_SCHEMA_VERSION;
	}

	public boolean isTombstone() {
		return status != SessionStatus.ACTIVE;
	}

	public boolean isActiveFor(
			UUID expectedUserId,
			int expectedCredentialVersion,
			Instant observedAt) {
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		return status == SessionStatus.ACTIVE
				&& userId.equals(expectedUserId)
				&& credentialVersion == expectedCredentialVersion
				&& requiredObservedAt.isBefore(idleExpiresAt)
				&& requiredObservedAt.isBefore(absoluteExpiresAt);
	}

	public Duration timeToLive(
			Instant observedAt,
			Duration maximumActiveTtl) {
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		Duration requiredMaximumActiveTtl = Objects.requireNonNull(
				maximumActiveTtl,
				"maximumActiveTtl must not be null");
		if (requiredMaximumActiveTtl.isZero()
				|| requiredMaximumActiveTtl.isNegative()) {
			throw new IllegalArgumentException(
					"maximumActiveTtl must be positive");
		}

		Instant effectiveExpiration = isTombstone()
				? absoluteExpiresAt
				: earlier(idleExpiresAt, absoluteExpiresAt);
		if (!effectiveExpiration.isAfter(requiredObservedAt)) {
			return Duration.ZERO;
		}

		Duration remaining = Duration.between(
				requiredObservedAt,
				effectiveExpiration);
		if (!isTombstone()
				&& remaining.compareTo(requiredMaximumActiveTtl) > 0) {
			return requiredMaximumActiveTtl;
		}
		return remaining;
	}

	private static Instant earlier(Instant first, Instant second) {
		return first.isBefore(second) ? first : second;
	}
}
