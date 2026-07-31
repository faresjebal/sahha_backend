package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.ToString;

import com.sahha.auth.entity.UserSession;
import com.sahha.auth.service.refreshtokenservice.IssuedRefreshToken;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class IssuedSessionCredentials {

	@ToString.Include
	private final UUID userId;

	@ToString.Include
	private final UUID sessionId;

	@Getter(onMethod_ = @JsonIgnore)
	private final String rawRefreshToken;

	@ToString.Include
	private final Instant refreshTokenExpiresAt;

	private final Instant idleExpiresAt;
	private final Instant absoluteExpiresAt;
	private final int credentialVersion;

	private IssuedSessionCredentials(
			UUID userId,
			UUID sessionId,
			String rawRefreshToken,
			Instant refreshTokenExpiresAt,
			Instant idleExpiresAt,
			Instant absoluteExpiresAt,
			int credentialVersion) {
		this.userId = Objects.requireNonNull(userId, "userId must not be null");
		this.sessionId = Objects.requireNonNull(
				sessionId,
				"sessionId must not be null");
		this.rawRefreshToken = requireToken(rawRefreshToken);
		this.refreshTokenExpiresAt = Objects.requireNonNull(
				refreshTokenExpiresAt,
				"refreshTokenExpiresAt must not be null");
		this.idleExpiresAt = Objects.requireNonNull(
				idleExpiresAt,
				"idleExpiresAt must not be null");
		this.absoluteExpiresAt = Objects.requireNonNull(
				absoluteExpiresAt,
				"absoluteExpiresAt must not be null");
		this.credentialVersion = credentialVersion;
	}

	public static IssuedSessionCredentials from(
			UserSession session,
			IssuedRefreshToken refreshToken) {
		UserSession requiredSession = Objects.requireNonNull(
				session,
				"session must not be null");
		IssuedRefreshToken requiredToken = Objects.requireNonNull(
				refreshToken,
				"refreshToken must not be null");
		if (!requiredSession.getId().equals(requiredToken.getSessionId())) {
			throw new IllegalArgumentException(
					"refreshToken must belong to the supplied session");
		}
		return new IssuedSessionCredentials(
				requiredSession.getUser().getId(),
				requiredSession.getId(),
				requiredToken.getRawToken(),
				requiredToken.getExpiresAt(),
				requiredSession.getIdleExpiresAt(),
				requiredSession.getAbsoluteExpiresAt(),
				requiredSession.getCredentialVersionAtCreation());
	}

	private static String requireToken(String rawToken) {
		Objects.requireNonNull(rawToken, "rawRefreshToken must not be null");
		if (rawToken.isBlank()) {
			throw new IllegalArgumentException(
					"rawRefreshToken must not be blank");
		}
		return rawToken;
	}
}
