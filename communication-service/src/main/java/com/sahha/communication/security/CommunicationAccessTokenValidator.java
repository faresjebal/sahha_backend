package com.sahha.communication.security;

import java.util.Collection;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CommunicationAccessTokenValidator implements OAuth2TokenValidator<Jwt> {
	public static final String ACTIVE_ORGANISATION_ID_CLAIM = "org_id";
	public static final String ORGANISATION_ROLES_CLAIM = "org_roles";
	public static final String PLATFORM_ROLES_CLAIM = "roles";
	private static final Pattern ROLE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
	private static final OAuth2Error ERROR = new OAuth2Error(
			"invalid_token", "The access token is invalid.", null);

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		try {
			if (!"access".equals(token.getClaimAsString("token_type"))) return failure();
			UUID.fromString(token.getSubject());
			UUID.fromString(token.getClaimAsString("sid"));
			Number version = token.getClaim("cv");
			if (version == null || version.intValue() < 1
					|| !validRoles(token.getClaims().get(PLATFORM_ROLES_CLAIM))
					|| !validRoles(token.getClaims().get(ORGANISATION_ROLES_CLAIM))) {
				return failure();
			}
			Collection<?> roles = (Collection<?>) token.getClaims().get(ORGANISATION_ROLES_CLAIM);
			String organisationId = token.getClaimAsString(ACTIVE_ORGANISATION_ID_CLAIM);
			if (roles.isEmpty() != (organisationId == null || organisationId.isBlank())) {
				return failure();
			}
			if (!roles.isEmpty()) UUID.fromString(organisationId);
			return OAuth2TokenValidatorResult.success();
		}
		catch (RuntimeException invalid) { return failure(); }
	}

	private static boolean validRoles(Object claim) {
		return claim instanceof Collection<?> values
				&& values.stream().allMatch(value -> value instanceof String role
						&& ROLE.matcher(role).matches());
	}

	private static OAuth2TokenValidatorResult failure() {
		return OAuth2TokenValidatorResult.failure(ERROR);
	}
}
