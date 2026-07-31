package com.sahha.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.stereotype.Component;

import com.sahha.auth.config.AuthJwtProperties;
import com.sahha.auth.service.usersessionservice.UserSessionCacheService;

@Component
public class SessionBoundJwtValidator implements OAuth2TokenValidator<Jwt> {

	public static final String SESSION_ID_CLAIM = "sid";
	public static final String CREDENTIAL_VERSION_CLAIM = "cv";
	public static final String PLATFORM_ROLES_CLAIM = "roles";
	public static final String TOKEN_TYPE_CLAIM = "token_type";
	public static final String ACCESS_TOKEN_TYPE = "access";

	private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
			"invalid_token",
			"The access token is invalid.",
			null);

	private final UserSessionCacheService sessionCacheService;
	private final Clock clock;

	public SessionBoundJwtValidator(
			UserSessionCacheService sessionCacheService,
			Clock clock) {
		this.sessionCacheService = sessionCacheService;
		this.clock = clock;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		try {
			if (!ACCESS_TOKEN_TYPE.equals(
					token.getClaimAsString(TOKEN_TYPE_CLAIM))) {
				return failure();
			}
			UUID userId = UUID.fromString(token.getSubject());
			UUID sessionId = UUID.fromString(
					token.getClaimAsString(SESSION_ID_CLAIM));
			Number credentialVersion =
					token.getClaim(CREDENTIAL_VERSION_CLAIM);
			if (credentialVersion == null
					|| credentialVersion.intValue() < 1) {
				return failure();
			}
			Instant observedAt = clock.instant();
			return sessionCacheService.isActive(
					sessionId,
					userId,
					credentialVersion.intValue(),
					observedAt)
					? OAuth2TokenValidatorResult.success()
					: failure();
		}
		catch (RuntimeException invalidClaims) {
			return failure();
		}
	}

	public OAuth2TokenValidator<Jwt> withStandardValidation(
			AuthJwtProperties properties) {
		OAuth2TokenValidator<Jwt> issuer =
				JwtValidators.createDefaultWithIssuer(properties.issuer());
		OAuth2TokenValidator<Jwt> audience = token ->
				token.getAudience().contains(properties.audience())
						? OAuth2TokenValidatorResult.success()
						: failure();
		return new DelegatingOAuth2TokenValidator<>(
				List.of(issuer, audience, this));
	}

	private static OAuth2TokenValidatorResult failure() {
		return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
	}
}
