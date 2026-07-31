package com.sahha.gateway.security;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayPlatformRoleConverterTests {

	@Test
	void mapsDistinctPlatformRolesToSpringRoleAuthorities() {
		Jwt jwt = Jwt.withTokenValue("aaa.bbb.ccc")
				.header("alg", "RS256")
				.subject("synthetic-user")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.claim(
						GatewayAccessTokenValidator.PLATFORM_ROLES_CLAIM,
						List.of(
								"PLATFORM_ADMIN",
								"PLATFORM_ADMIN",
								"PATIENT"))
				.build();

		assertEquals(
				List.of("ROLE_PLATFORM_ADMIN", "ROLE_PATIENT"),
				new GatewayPlatformRoleConverter()
						.convert(jwt)
						.stream()
						.map(authority -> authority.getAuthority())
						.toList());
	}
}
