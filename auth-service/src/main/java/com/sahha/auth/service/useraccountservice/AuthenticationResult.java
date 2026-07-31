package com.sahha.auth.service.useraccountservice;

import java.util.Optional;
import java.util.UUID;

import lombok.ToString;

@ToString
public final class AuthenticationResult {

	private static final AuthenticationResult DENIED =
			new AuthenticationResult(null);

	private final UUID userId;

	private AuthenticationResult(UUID userId) {
		this.userId = userId;
	}

	public static AuthenticationResult success(UUID userId) {
		if (userId == null) {
			throw new IllegalArgumentException("userId must not be null");
		}
		return new AuthenticationResult(userId);
	}

	public static AuthenticationResult denied() {
		return DENIED;
	}

	public boolean isAuthenticated() {
		return userId != null;
	}

	public Optional<UUID> authenticatedUserId() {
		return Optional.ofNullable(userId);
	}
}
