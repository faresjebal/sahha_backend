package com.sahha.notification.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

public final class NotificationPrincipalName {

	private static final String CONTEXT_SEPARATOR = "__";

	private NotificationPrincipalName() {
	}

	public static String from(Jwt jwt) {
		return of(userId(jwt), organisationId(jwt));
	}

	public static String of(UUID userId, UUID organisationId) {
		return userId + CONTEXT_SEPARATOR + organisationId;
	}

	public static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	public static UUID organisationId(Jwt jwt) {
		return UUID.fromString(jwt.getClaimAsString(
				NotificationAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM));
	}
}
