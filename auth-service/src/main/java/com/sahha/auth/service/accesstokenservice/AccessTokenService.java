package com.sahha.auth.service.accesstokenservice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.sahha.auth.config.AuthJwtProperties;
import com.sahha.auth.security.RsaKeyMaterial;
import com.sahha.auth.security.SessionBoundJwtValidator;
import com.sahha.auth.service.usersessionservice.IssuedSessionCredentials;

@Service
public class AccessTokenService {

	private final JwtEncoder jwtEncoder;
	private final RsaKeyMaterial keyMaterial;
	private final AuthJwtProperties properties;

	public AccessTokenService(
			JwtEncoder jwtEncoder,
			RsaKeyMaterial keyMaterial,
			AuthJwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.keyMaterial = keyMaterial;
		this.properties = properties;
	}

	public IssuedAccessToken issue(
			IssuedSessionCredentials session,
			List<String> platformRoles,
			Instant issuedAt) {
		IssuedSessionCredentials requiredSession = Objects.requireNonNull(
				session,
				"session must not be null");
		Instant requiredIssuedAt = Objects.requireNonNull(
				issuedAt,
				"issuedAt must not be null");
		List<String> requiredRoles = List.copyOf(
				Objects.requireNonNull(
						platformRoles,
						"platformRoles must not be null"));
		Instant expiresAt = earliest(
				requiredIssuedAt.plus(properties.accessTokenLifetime()),
				requiredSession.getIdleExpiresAt(),
				requiredSession.getAbsoluteExpiresAt());
		if (!expiresAt.isAfter(requiredIssuedAt)) {
			throw new IllegalStateException(
					"the session cannot receive a new access token");
		}

		JwsHeader header = JwsHeader
				.with(SignatureAlgorithm.RS256)
				.keyId(keyMaterial.keyId())
				.type("JWT")
				.build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.id(UUID.randomUUID().toString())
				.issuer(properties.issuer())
				.audience(List.of(properties.audience()))
				.subject(requiredSession.getUserId().toString())
				.issuedAt(requiredIssuedAt)
				.notBefore(requiredIssuedAt)
				.expiresAt(expiresAt)
				.claim(
						SessionBoundJwtValidator.SESSION_ID_CLAIM,
						requiredSession.getSessionId().toString())
				.claim(
						SessionBoundJwtValidator.CREDENTIAL_VERSION_CLAIM,
						requiredSession.getCredentialVersion())
				.claim(
						SessionBoundJwtValidator.PLATFORM_ROLES_CLAIM,
						requiredRoles)
				.claim(
						SessionBoundJwtValidator.TOKEN_TYPE_CLAIM,
						SessionBoundJwtValidator.ACCESS_TOKEN_TYPE)
				.build();
		String encoded = jwtEncoder.encode(
				JwtEncoderParameters.from(header, claims))
				.getTokenValue();
		return new IssuedAccessToken(encoded, requiredIssuedAt, expiresAt);
	}

	private static Instant earliest(
			Instant first,
			Instant second,
			Instant third) {
		Instant result = first.isBefore(second) ? first : second;
		return result.isBefore(third) ? result : third;
	}
}
