package com.sahha.gateway.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayAccessTokenValidatorTests {

	private final GatewayAccessTokenValidator validator =
			new GatewayAccessTokenValidator();

	@Test
	void acceptsTheMinimalSahhaAccessClaimContract() {
		assertFalse(validator.validate(jwt(
				"access",
				1,
				List.of("PLATFORM_ADMIN"))).hasErrors());
	}

	@Test
	void rejectsWrongTokenTypeAndInvalidSessionClaims() {
		assertInvalid(jwt("refresh", 1, List.of()));
		assertInvalid(Jwt.withTokenValue("aaa.bbb.ccc")
				.header("alg", "RS256")
				.subject(UUID.randomUUID().toString())
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.claim(GatewayAccessTokenValidator.SESSION_ID_CLAIM, "invalid")
				.claim(
						GatewayAccessTokenValidator.CREDENTIAL_VERSION_CLAIM,
						1)
				.claim(
						GatewayAccessTokenValidator.PLATFORM_ROLES_CLAIM,
						List.of())
				.claim(
						GatewayAccessTokenValidator.TOKEN_TYPE_CLAIM,
						"access")
				.build());
		assertInvalid(jwt("access", 0, List.of()));
	}

	@Test
	void rejectsMalformedOrMissingPlatformRoles() {
		assertInvalid(jwt("access", 1, List.of("platform-admin")));
		assertInvalid(Jwt.withTokenValue("aaa.bbb.ccc")
				.header("alg", "RS256")
				.subject(UUID.randomUUID().toString())
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.claim(
						GatewayAccessTokenValidator.SESSION_ID_CLAIM,
						UUID.randomUUID().toString())
				.claim(
						GatewayAccessTokenValidator.CREDENTIAL_VERSION_CLAIM,
						1)
				.claim(
						GatewayAccessTokenValidator.TOKEN_TYPE_CLAIM,
						"access")
				.build());
	}

	private void assertInvalid(Jwt jwt) {
		OAuth2TokenValidatorResult result = validator.validate(jwt);
		assertTrue(result.hasErrors());
	}

	private static Jwt jwt(
			String tokenType,
			int credentialVersion,
			List<String> roles) {
		Instant now = Instant.now();
		return Jwt.withTokenValue("aaa.bbb.ccc")
				.header("alg", "RS256")
				.subject(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim(
						GatewayAccessTokenValidator.SESSION_ID_CLAIM,
						UUID.randomUUID().toString())
				.claim(
						GatewayAccessTokenValidator.CREDENTIAL_VERSION_CLAIM,
						credentialVersion)
				.claim(
						GatewayAccessTokenValidator.PLATFORM_ROLES_CLAIM,
						roles)
				.claim(
						GatewayAccessTokenValidator.TOKEN_TYPE_CLAIM,
						tokenType)
				.build();
	}
}
