package com.sahha.organisation.security;

import java.util.Collection;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class OrganisationAccessTokenValidator
		implements OAuth2TokenValidator<Jwt> {

	public static final String SESSION_ID_CLAIM = "sid";
	public static final String CREDENTIAL_VERSION_CLAIM = "cv";
	public static final String PLATFORM_ROLES_CLAIM = "roles";
	public static final String ACTIVE_ORGANISATION_ID_CLAIM = "org_id";
	public static final String ORGANISATION_ROLES_CLAIM = "org_roles";
	public static final String TOKEN_TYPE_CLAIM = "token_type";
	public static final String ACCESS_TOKEN_TYPE = "access";

	private static final Pattern ROLE_CODE =
			Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
	private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
			"invalid_token",
			"The access token is invalid.",
			null);

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		try {
			if (!ACCESS_TOKEN_TYPE.equals(
					token.getClaimAsString(TOKEN_TYPE_CLAIM))) {
				return failure();
			}
			UUID.fromString(token.getSubject());
			UUID.fromString(token.getClaimAsString(SESSION_ID_CLAIM));
			Number credentialVersion =
					token.getClaim(CREDENTIAL_VERSION_CLAIM);
			if (credentialVersion == null
					|| credentialVersion.intValue() < 1) {
				return failure();
			}
			Object roles = token.getClaims().get(PLATFORM_ROLES_CLAIM);
			if (!validRoles(roles)) {
				return failure();
			}
			String organisationId = token.getClaimAsString(
					ACTIVE_ORGANISATION_ID_CLAIM);
			Object organisationRoles = token.getClaims().get(
					ORGANISATION_ROLES_CLAIM);
			if (!validRoles(organisationRoles)) {
				return failure();
			}
			Collection<?> organisationRoleValues =
					(Collection<?>) organisationRoles;
			if ((organisationId == null) != organisationRoleValues.isEmpty()) {
				return failure();
			}
			if (organisationId != null) {
				UUID.fromString(organisationId);
			}
			return OAuth2TokenValidatorResult.success();
		}
		catch (RuntimeException invalidClaim) {
			return failure();
		}
	}

	private static boolean validRoles(Object claim) {
		return claim instanceof Collection<?> values
				&& values.stream().allMatch(
						role -> role instanceof String value
								&& ROLE_CODE.matcher(value).matches());
	}

	private static OAuth2TokenValidatorResult failure() {
		return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
	}
}
