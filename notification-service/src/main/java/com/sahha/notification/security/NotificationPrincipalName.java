package com.sahha.notification.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

public final class NotificationPrincipalName {

	private static final String CONTEXT_SEPARATOR = "__";

	private NotificationPrincipalName() {
	}

	public static String from(Jwt jwt) {
		String organisation = jwt.getClaimAsString(NotificationAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM);
		if (organisation == null || organisation.isBlank()) return "account__" + userId(jwt);
		return of(userId(jwt), organisationId(jwt));
	}

	public static String of(UUID userId, UUID organisationId) {
		return userId + CONTEXT_SEPARATOR + organisationId;
	}

	public static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	public static UUID organisationId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getClaimAsString(
					NotificationAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM));
		} catch (RuntimeException missing) {
			throw new com.sahha.notification.exception.NotificationAccessDeniedException();
		}
	}
}
