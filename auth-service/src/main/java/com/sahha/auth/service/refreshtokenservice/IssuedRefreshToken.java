package com.sahha.auth.service.refreshtokenservice;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class IssuedRefreshToken {

	@ToString.Include
	private final UUID tokenId;

	@ToString.Include
	private final UUID sessionId;

	@Getter(onMethod_ = @JsonIgnore)
	private final String rawToken;

	@ToString.Include
	private final Instant expiresAt;

	public IssuedRefreshToken(
			UUID tokenId,
			UUID sessionId,
			String rawToken,
			Instant expiresAt) {
		this.tokenId = Objects.requireNonNull(tokenId, "tokenId must not be null");
		this.sessionId = Objects.requireNonNull(
				sessionId,
				"sessionId must not be null");
		this.rawToken = requireToken(rawToken);
		this.expiresAt = Objects.requireNonNull(
				expiresAt,
				"expiresAt must not be null");
	}

	private static String requireToken(String rawToken) {
		Objects.requireNonNull(rawToken, "rawToken must not be null");
		if (rawToken.isBlank()) {
			throw new IllegalArgumentException("rawToken must not be blank");
		}
		return rawToken;
	}
}
