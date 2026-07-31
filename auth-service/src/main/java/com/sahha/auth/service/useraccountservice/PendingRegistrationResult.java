package com.sahha.auth.service.useraccountservice;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sahha.auth.service.verificationtokenservice.IssuedVerificationToken;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class PendingRegistrationResult {

	@ToString.Include
	private final UUID userId;

	@Getter(onMethod_ = @JsonIgnore)
	private final IssuedVerificationToken verificationToken;

	public PendingRegistrationResult(
			UUID userId,
			IssuedVerificationToken verificationToken) {
		this.userId = Objects.requireNonNull(userId, "userId must not be null");
		this.verificationToken = Objects.requireNonNull(
				verificationToken,
				"verificationToken must not be null");
	}
}
