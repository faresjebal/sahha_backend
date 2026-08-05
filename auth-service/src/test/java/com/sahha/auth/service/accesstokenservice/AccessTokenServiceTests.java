package com.sahha.auth.service.accesstokenservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.sahha.auth.config.AuthJwtProperties;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.security.RsaKeyMaterial;
import com.sahha.auth.security.SessionBoundJwtValidator;
import com.sahha.auth.service.refreshtokenservice.IssuedRefreshToken;
import com.sahha.auth.service.usersessionservice.IssuedSessionCredentials;

class AccessTokenServiceTests {

	private static final Instant NOW =
			Instant.parse("2026-07-28T12:00:00Z");

	@Test
	void signsMinimalRs256ClaimsWithoutIdentityOrRefreshSecrets()
			throws Exception {
		KeyPair pair = rsaKeyPair();
		RsaKeyMaterial keyMaterial = new RsaKeyMaterial(
				(RSAPublicKey) pair.getPublic(),
				(RSAPrivateKey) pair.getPrivate(),
				"test-key");
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(
				new ImmutableJWKSet<SecurityContext>(
						new JWKSet(keyMaterial.signingKey())));
		AccessTokenService service = new AccessTokenService(
				encoder,
				keyMaterial,
				properties(Duration.ofMinutes(10)));
		IssuedSessionCredentials session = credentials(
				NOW.plus(Duration.ofDays(7)),
				NOW.plus(Duration.ofDays(30)));

		IssuedAccessToken issued = service.issue(
				session,
				List.of("PLATFORM_ADMIN"),
				NOW);
		NimbusJwtDecoder decoder = NimbusJwtDecoder
				.withPublicKey(keyMaterial.publicKey())
				.build();
		decoder.setJwtValidator(
				token -> OAuth2TokenValidatorResult.success());
		Jwt decoded = decoder.decode(issued.getRawToken());

		assertEquals(session.getUserId().toString(), decoded.getSubject());
		assertEquals(
				session.getSessionId().toString(),
				decoded.getClaimAsString(
						SessionBoundJwtValidator.SESSION_ID_CLAIM));
		assertEquals(
				session.getCredentialVersion(),
				((Number) decoded.getClaim(
						SessionBoundJwtValidator.CREDENTIAL_VERSION_CLAIM))
						.intValue());
		assertEquals(
				List.of("PLATFORM_ADMIN"),
				decoded.getClaimAsStringList(
						SessionBoundJwtValidator.PLATFORM_ROLES_CLAIM));
		assertEquals(
				SessionBoundJwtValidator.ACCESS_TOKEN_TYPE,
				decoded.getClaimAsString(
						SessionBoundJwtValidator.TOKEN_TYPE_CLAIM));
		assertEquals(NOW.plusSeconds(600), issued.getExpiresAt());
		assertFalse(decoded.hasClaim("email"));
		assertFalse(decoded.hasClaim("name"));
		assertFalse(decoded.hasClaim("refresh_token"));
		assertFalse(issued.toString().contains(issued.getRawToken()));
	}

	@Test
	void accessExpirationNeverOutlivesTheSession() throws Exception {
		KeyPair pair = rsaKeyPair();
		RsaKeyMaterial keyMaterial = new RsaKeyMaterial(
				(RSAPublicKey) pair.getPublic(),
				(RSAPrivateKey) pair.getPrivate(),
				"bounded-key");
		AccessTokenService service = new AccessTokenService(
				new NimbusJwtEncoder(
						new ImmutableJWKSet<SecurityContext>(
								new JWKSet(keyMaterial.signingKey()))),
				keyMaterial,
				properties(Duration.ofMinutes(10)));
		IssuedSessionCredentials session = credentials(
				NOW.plusSeconds(90),
				NOW.plus(Duration.ofDays(1)));

		IssuedAccessToken issued = service.issue(session, List.of(), NOW);

		assertEquals(NOW.plusSeconds(90), issued.getExpiresAt());
		assertTrue(issued.getExpiresAt().isAfter(issued.getIssuedAt()));
	}

	@Test
	void rejectsLongLivedAccessTokensAndMissingProductionKeys() {
		assertThrows(
				IllegalArgumentException.class,
				() -> properties(Duration.ofMinutes(16)));
		assertThrows(
				IllegalArgumentException.class,
				() -> new AuthJwtProperties(
						Duration.ofMinutes(10),
						"http://localhost:8081",
						"sahha-api",
						false,
						"",
						""));
	}

	private static AuthJwtProperties properties(Duration lifetime) {
		return new AuthJwtProperties(
				lifetime,
				"http://localhost:8081",
				"sahha-api",
				true,
				"",
				"");
	}

	private static IssuedSessionCredentials credentials(
			Instant idleExpiresAt,
			Instant absoluteExpiresAt) {
		UserAccount user = UserAccount.pendingRegistration(
				"synthetic.jwt@example.com",
				"synthetic.jwt@example.com",
				"bcrypt-hash",
				"Synthetic",
				"JWT",
				null,
				NOW.minusSeconds(60));
		user.verifyEmail(NOW.minusSeconds(59));
		UserSession session = UserSession.open(
				user,
				"0".repeat(64),
				"Test device",
				"Synthetic browser",
				"192.0.2.1",
				NOW.minusSeconds(1),
				idleExpiresAt,
				absoluteExpiresAt);
		IssuedRefreshToken refreshToken = new IssuedRefreshToken(
				java.util.UUID.randomUUID(),
				session.getId(),
				"synthetic-refresh-secret",
				idleExpiresAt);
		return IssuedSessionCredentials.from(session, refreshToken);
	}

	private static KeyPair rsaKeyPair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}
}
