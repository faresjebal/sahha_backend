package com.sahha.auth.service.accesstokenservice;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class IssuedAccessToken {

	@Getter(onMethod_ = @JsonIgnore)
	private final String rawToken;

	@ToString.Include
	private final Instant issuedAt;

	@ToString.Include
	private final Instant expiresAt;

	public IssuedAccessToken(
			String rawToken,
			Instant issuedAt,
			Instant expiresAt) {
		this.rawToken = requireToken(rawToken);
		this.issuedAt = Objects.requireNonNull(
				issuedAt,
				"issuedAt must not be null");
		this.expiresAt = Objects.requireNonNull(
				expiresAt,
				"expiresAt must not be null");
		if (!this.expiresAt.isAfter(this.issuedAt)) {
			throw new IllegalArgumentException(
					"expiresAt must follow issuedAt");
		}
	}

	private static String requireToken(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("rawToken must not be blank");
		}
		return value;
	}
}
