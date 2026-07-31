package com.sahha.organisation.config;

import java.net.URI;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.organisation.security")
public record OrganisationSecurityProperties(
		@DefaultValue("SAHHA_ACCESS_TOKEN")
		String accessTokenCookieName,

		@DefaultValue("http://localhost:8081/.well-known/jwks.json")
		URI jwkSetUri,

		@DefaultValue("http://localhost:8081")
		String issuer,

		@DefaultValue("sahha-api")
		String audience,

		@DefaultValue("XSRF-TOKEN")
		String csrfTokenName,

		@DefaultValue("X-XSRF-TOKEN")
		String csrfHeaderName,

		@DefaultValue("false")
		boolean secureCookies) {

	private static final Pattern SAFE_TOKEN =
			Pattern.compile("^[A-Za-z0-9_-]+$");

	public OrganisationSecurityProperties {
		accessTokenCookieName = requireToken(
				accessTokenCookieName,
				"accessTokenCookieName");
		jwkSetUri = requireHttpUri(jwkSetUri, "jwkSetUri");
		issuer = requireText(issuer, "issuer");
		audience = requireText(audience, "audience");
		csrfTokenName = requireToken(csrfTokenName, "csrfTokenName");
		csrfHeaderName = requireToken(csrfHeaderName, "csrfHeaderName");
	}

	private static String requireToken(String value, String fieldName) {
		String normalized = requireText(value, fieldName);
		if (!SAFE_TOKEN.matcher(normalized).matches()) {
			throw new IllegalArgumentException(
					fieldName + " must be a safe HTTP token");
		}
		return normalized;
	}

	private static URI requireHttpUri(URI value, String fieldName) {
		if (value == null
				|| value.getScheme() == null
				|| value.getHost() == null
				|| (!"http".equalsIgnoreCase(value.getScheme())
						&& !"https".equalsIgnoreCase(value.getScheme()))) {
			throw new IllegalArgumentException(
					fieldName + " must be an absolute HTTP(S) URI");
		}
		return value;
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					fieldName + " must not be blank");
		}
		return value.strip();
	}
}
