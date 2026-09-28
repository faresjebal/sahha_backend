package com.sahha.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.stereotype.Component;

import com.sahha.auth.config.AuthJwtProperties;
import com.sahha.auth.service.usersessionservice.AuthoritativeSessionService;

@Component
public class SessionBoundJwtValidator implements OAuth2TokenValidator<Jwt> {

	public static final String SESSION_ID_CLAIM = "sid";
	public static final String CREDENTIAL_VERSION_CLAIM = "cv";
	public static final String PLATFORM_ROLES_CLAIM = "roles";
	public static final String ACTIVE_ORGANISATION_ID_CLAIM = "org_id";
	public static final String ORGANISATION_ROLES_CLAIM = "org_roles";
	public static final String TOKEN_TYPE_CLAIM = "token_type";
	public static final String ACCESS_TOKEN_TYPE = "access";

	private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
			"invalid_token",
			"The access token is invalid.",
			null);
	private static final Pattern ROLE_CODE =
			Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

	private final AuthoritativeSessionService sessionService;
	private final Clock clock;

	public SessionBoundJwtValidator(
			AuthoritativeSessionService sessionService,
			Clock clock) {
		this.sessionService = sessionService;
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
					|| credentialVersion.intValue() < 1
					|| credentialVersion.doubleValue() != credentialVersion.intValue()) {
				return failure();
			}
			String organisationIdClaim = token.getClaimAsString(
					ACTIVE_ORGANISATION_ID_CLAIM);
			UUID activeOrganisationId = organisationIdClaim == null
					? null
					: UUID.fromString(organisationIdClaim);
			Object roleClaim = token.getClaims().get(ORGANISATION_ROLES_CLAIM);
			if (!(roleClaim instanceof Collection<?> values)
					|| values.stream().anyMatch(value ->
							!(value instanceof String role)
									|| !ROLE_CODE.matcher(role).matches())) {
				return failure();
			}
			List<String> organisationRoles = values.stream()
					.map(String.class::cast)
					.distinct()
					.sorted()
					.toList();
			if (organisationRoles.size() != values.size()
					|| (activeOrganisationId == null)
							!= organisationRoles.isEmpty()) {
				return failure();
			}
			Object platformClaim = token.getClaims().get(PLATFORM_ROLES_CLAIM);
			if (!(platformClaim instanceof Collection<?> platformValues)
					|| platformValues.stream().anyMatch(value -> !(value instanceof String role)
							|| !ROLE_CODE.matcher(role).matches())) {
				return failure();
			}
			List<String> platformRoles = platformValues.stream().map(String.class::cast)
					.distinct().sorted().toList();
			if (platformRoles.size() != platformValues.size()) return failure();
			Instant observedAt = clock.instant();
			return sessionService.isActiveForContext(
					sessionId,
					userId,
					credentialVersion.intValue(),
					activeOrganisationId,
					organisationRoles,
					platformRoles,
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
