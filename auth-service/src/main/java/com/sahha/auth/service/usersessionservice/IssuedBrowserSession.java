package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.ToString;

import com.sahha.auth.service.accesstokenservice.IssuedAccessToken;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class IssuedBrowserSession {

	@ToString.Include
	private final UUID userId;

	@ToString.Include
	private final UUID sessionId;

	@Getter(onMethod_ = @JsonIgnore)
	private final String rawAccessToken;

	@Getter(onMethod_ = @JsonIgnore)
	private final String rawRefreshToken;

	@ToString.Include
	private final Instant accessTokenExpiresAt;

	@ToString.Include
	private final Instant refreshTokenExpiresAt;

	private final Instant idleExpiresAt;
	private final Instant absoluteExpiresAt;
	private final List<String> platformRoles;

	public IssuedBrowserSession(
			IssuedSessionCredentials session,
			IssuedAccessToken accessToken,
			List<String> platformRoles) {
		IssuedSessionCredentials requiredSession = Objects.requireNonNull(
				session,
				"session must not be null");
		IssuedAccessToken requiredAccessToken = Objects.requireNonNull(
				accessToken,
				"accessToken must not be null");
		this.userId = requiredSession.getUserId();
		this.sessionId = requiredSession.getSessionId();
		this.rawAccessToken = requiredAccessToken.getRawToken();
		this.rawRefreshToken = requiredSession.getRawRefreshToken();
		this.accessTokenExpiresAt = requiredAccessToken.getExpiresAt();
		this.refreshTokenExpiresAt =
				requiredSession.getRefreshTokenExpiresAt();
		this.idleExpiresAt = requiredSession.getIdleExpiresAt();
		this.absoluteExpiresAt = requiredSession.getAbsoluteExpiresAt();
		this.platformRoles = List.copyOf(
				Objects.requireNonNull(
						platformRoles,
						"platformRoles must not be null"));
	}
}
