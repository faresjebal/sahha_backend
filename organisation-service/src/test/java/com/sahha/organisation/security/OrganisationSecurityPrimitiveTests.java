package com.sahha.organisation.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OrganisationSecurityPrimitiveTests {

	private final OrganisationAccessTokenValidator validator =
			new OrganisationAccessTokenValidator();

	@Test
	void validAuthAccessClaimsAreAccepted() {
		assertFalse(validator.validate(jwt(
				UUID.randomUUID().toString(),
				UUID.randomUUID().toString(),
				1,
				List.of("PLATFORM_ADMIN"),
				"access")).hasErrors());
	}

	@Test
	void malformedIdentitySessionVersionRoleOrTypeIsRejected() {
		assertTrue(validator.validate(jwt(
				"not-a-uuid",
				UUID.randomUUID().toString(),
				1,
				List.of("PLATFORM_ADMIN"),
				"access")).hasErrors());
		assertTrue(validator.validate(jwt(
				UUID.randomUUID().toString(),
				"not-a-uuid",
				1,
				List.of("PLATFORM_ADMIN"),
				"access")).hasErrors());
		assertTrue(validator.validate(jwt(
				UUID.randomUUID().toString(),
				UUID.randomUUID().toString(),
				0,
				List.of("PLATFORM_ADMIN"),
				"access")).hasErrors());
		assertTrue(validator.validate(jwt(
				UUID.randomUUID().toString(),
				UUID.randomUUID().toString(),
				1,
				List.of("platform-admin"),
				"access")).hasErrors());
		assertTrue(validator.validate(jwt(
				UUID.randomUUID().toString(),
				UUID.randomUUID().toString(),
				1,
				List.of("PLATFORM_ADMIN"),
				"refresh")).hasErrors());
	}

	@Test
	void converterMapsOnlyPlatformRolesToSpringAuthorities() {
		var authorities = new OrganisationPlatformRoleConverter()
				.convert(jwt(
						UUID.randomUUID().toString(),
						UUID.randomUUID().toString(),
						1,
						List.of("PLATFORM_ADMIN", "PLATFORM_ADMIN"),
						"access"));

		assertEquals(
				List.of("ROLE_PLATFORM_ADMIN"),
				authorities.stream()
						.map(authority -> authority.getAuthority())
						.toList());
	}

	private static Jwt jwt(
			String subject,
			String sessionId,
			int credentialVersion,
			List<String> roles,
			String tokenType) {
		Instant now = Instant.now();
		return Jwt.withTokenValue("aaa.bbb.ccc")
				.header("alg", "RS256")
				.subject(subject)
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", sessionId)
				.claim("cv", credentialVersion)
				.claim("roles", roles)
				.claim("org_roles", List.of())
				.claim("token_type", tokenType)
				.build();
	}
}
