package com.sahha.notification.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NotificationPrincipalNameTests {

	@Test
	void oneUserHasADifferentPrincipalInEveryActiveOrganisation() {
		UUID userId = UUID.randomUUID();
		UUID firstOrganisation = UUID.randomUUID();
		UUID secondOrganisation = UUID.randomUUID();

		assertNotEquals(
				NotificationPrincipalName.of(userId, firstOrganisation),
				NotificationPrincipalName.of(userId, secondOrganisation));
	}

	@Test
	void authenticationNameIsBoundToTheJwtUserAndActiveOrganisation() {
		UUID userId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		Jwt jwt = jwt(userId, organisationId);

		JwtAuthenticationToken authentication = (JwtAuthenticationToken)
				new NotificationJwtAuthenticationConverter().convert(jwt);

		assertEquals(
				NotificationPrincipalName.of(userId, organisationId),
				authentication.getName());
		assertEquals(jwt, authentication.getToken());
	}

	private static Jwt jwt(UUID userId, UUID organisationId) {
		Instant now = Instant.now();
		return Jwt.withTokenValue("aaa.bbb.ccc")
				.header("alg", "RS256")
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim(
						NotificationAccessTokenValidator
								.ACTIVE_ORGANISATION_ID_CLAIM,
						organisationId.toString())
				.claim("roles", List.of("DOCTOR"))
				.build();
	}
}
