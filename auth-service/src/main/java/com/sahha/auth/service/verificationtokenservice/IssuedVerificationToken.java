package com.sahha.auth.service.verificationtokenservice;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sahha.auth.entity.VerificationTokenPurpose;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class IssuedVerificationToken {

	@ToString.Include
	private final UUID tokenId;

	@Getter(onMethod_ = @JsonIgnore)
	private final String rawToken;

	@ToString.Include
	private final VerificationTokenPurpose purpose;

	@ToString.Include
	private final Instant expiresAt;

	public IssuedVerificationToken(
			UUID tokenId,
			String rawToken,
			VerificationTokenPurpose purpose,
			Instant expiresAt) {
		this.tokenId = Objects.requireNonNull(tokenId, "tokenId must not be null");
		this.rawToken = Objects.requireNonNull(
				rawToken,
				"rawToken must not be null");
		this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
		this.expiresAt = Objects.requireNonNull(
				expiresAt,
				"expiresAt must not be null");
	}
}
