package com.sahha.communication.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CommunicationPrincipalName {
	private static final String SEPARATOR = "__";
	private CommunicationPrincipalName() { }

	public static String from(Jwt jwt) {
		return of(UUID.fromString(jwt.getSubject()),
				UUID.fromString(jwt.getClaimAsString(
						CommunicationAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM)));
	}

	public static String of(UUID userId, UUID organisationId) {
		return userId + SEPARATOR + organisationId;
	}
}
