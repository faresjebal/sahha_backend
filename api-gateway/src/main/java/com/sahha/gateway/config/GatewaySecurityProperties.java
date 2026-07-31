package com.sahha.gateway.config;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.gateway.security")
public record GatewaySecurityProperties(
		@DefaultValue("SAHHA_ACCESS_TOKEN")
		String accessTokenCookieName,

		@DefaultValue("http://localhost:8081/.well-known/jwks.json")
		URI jwkSetUri,

		@DefaultValue("http://localhost:8081")
		String issuer,

		@DefaultValue("sahha-api")
		String audience,

		@DefaultValue("http://localhost:5173")
		List<String> allowedOrigins) {

	private static final Pattern SAFE_TOKEN =
			Pattern.compile("^[A-Za-z0-9_-]+$");

	public GatewaySecurityProperties {
		accessTokenCookieName = requireToken(
				accessTokenCookieName,
				"accessTokenCookieName");
		jwkSetUri = requireHttpUri(jwkSetUri, "jwkSetUri");
		issuer = requireText(issuer, "issuer");
		audience = requireText(audience, "audience");
		if (allowedOrigins == null || allowedOrigins.isEmpty()) {
			throw new IllegalArgumentException(
					"allowedOrigins must contain at least one origin");
		}
		allowedOrigins = allowedOrigins.stream()
				.map(GatewaySecurityProperties::requireOrigin)
				.distinct()
				.toList();
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

	private static String requireOrigin(String value) {
		String normalized = requireText(value, "allowedOrigin");
		URI origin;
		try {
			origin = URI.create(normalized);
		}
		catch (IllegalArgumentException invalidOrigin) {
			throw new IllegalArgumentException(
					"allowedOrigin must be a valid HTTP(S) origin",
					invalidOrigin);
		}
		requireHttpUri(origin, "allowedOrigin");
		if ((origin.getPath() != null && !origin.getPath().isEmpty())
				|| origin.getQuery() != null
				|| origin.getFragment() != null
				|| origin.getUserInfo() != null) {
			throw new IllegalArgumentException(
					"allowedOrigin must not contain a path, query, fragment, or user info");
		}
		return normalized;
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					fieldName + " must not be blank");
		}
		return value.strip();
	}
}
