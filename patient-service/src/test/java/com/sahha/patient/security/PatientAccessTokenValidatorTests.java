package com.sahha.patient.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class PatientAccessTokenValidatorTests {

	private final PatientAccessTokenValidator validator =
			new PatientAccessTokenValidator();

	@Test
	void acceptsUnscopedPatientTokenAndRejectsUnpairedOrganisationContext() {
		assertFalse(validator.validate(token(null, List.of())).hasErrors());
		assertTrue(validator.validate(token(
				UUID.randomUUID().toString(), List.of())).hasErrors());
		assertTrue(validator.validate(token(
				null, List.of("RECEPTIONIST"))).hasErrors());
	}

	private static Jwt token(String organisationId, List<String> roles) {
		Instant now = Instant.now();
		Jwt.Builder builder = Jwt.withTokenValue("test")
				.header("alg", "RS256")
				.subject(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of())
				.claim("org_roles", roles)
				.claim("token_type", "access");
		if (organisationId != null) builder.claim("org_id", organisationId);
		return builder.build();
	}
}
