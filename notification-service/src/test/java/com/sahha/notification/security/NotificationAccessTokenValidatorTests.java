package com.sahha.notification.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class NotificationAccessTokenValidatorTests {

	private final NotificationAccessTokenValidator validator =
			new NotificationAccessTokenValidator();

	@Test
	void acceptsAnAccessTokenWithPairedActiveOrganisationClaims() {
		assertFalse(validator.validate(jwt(
				"access",
				UUID.randomUUID().toString(),
				List.of("DOCTOR"))).hasErrors());
	}

	@Test
	void rejectsRefreshTokensMissingOrEmptyOrganisationContext() {
		assertTrue(validator.validate(jwt(
				"refresh",
				UUID.randomUUID().toString(),
				List.of("DOCTOR"))).hasErrors());
		assertTrue(validator.validate(jwt(
				"access",
				null,
				List.of("DOCTOR"))).hasErrors());
		assertTrue(validator.validate(jwt(
				"access",
				UUID.randomUUID().toString(),
				List.of())).hasErrors());
	}

	private static Jwt jwt(
			String tokenType,
			String organisationId,
			List<String> organisationRoles) {
		Instant now = Instant.now();
		Jwt.Builder builder = Jwt.withTokenValue("aaa.bbb.ccc")
				.header("alg", "RS256")
				.subject(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of())
				.claim("org_roles", organisationRoles)
				.claim("token_type", tokenType);
		if (organisationId != null) {
			builder.claim("org_id", organisationId);
		}
		return builder.build();
	}
}
