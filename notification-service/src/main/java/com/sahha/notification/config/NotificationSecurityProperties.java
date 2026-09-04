package com.sahha.notification.config;

import java.net.URI;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.notification.security")
public record NotificationSecurityProperties(
		@DefaultValue("SAHHA_ACCESS_TOKEN") String accessTokenCookieName,
		@DefaultValue("http://localhost:8081/.well-known/jwks.json") URI jwkSetUri,
		@DefaultValue("http://localhost:8081") String issuer,
		@DefaultValue("sahha-api") String audience,
		@DefaultValue("XSRF-TOKEN") String csrfTokenName,
		@DefaultValue("X-XSRF-TOKEN") String csrfHeaderName,
		@DefaultValue("false") boolean secureCookies,
		@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {

	public NotificationSecurityProperties {
		if (accessTokenCookieName == null || accessTokenCookieName.isBlank()
				|| csrfTokenName == null || csrfTokenName.isBlank()
				|| csrfHeaderName == null || csrfHeaderName.isBlank()
				|| issuer == null || issuer.isBlank()
				|| audience == null || audience.isBlank()
				|| jwkSetUri == null || jwkSetUri.getScheme() == null) {
			throw new IllegalArgumentException(
					"Notification security properties are invalid");
		}
		if (allowedOrigins == null || allowedOrigins.isEmpty()
				|| allowedOrigins.stream().anyMatch(
						origin -> origin == null || origin.isBlank())) {
			throw new IllegalArgumentException(
					"Notification allowed origins are invalid");
		}
		allowedOrigins = allowedOrigins.stream()
				.map(String::strip)
				.map(URI::create)
				.peek(NotificationSecurityProperties::requireHttpOrigin)
				.map(URI::toString)
				.distinct()
				.toList();
	}

	private static void requireHttpOrigin(URI origin) {
		if (origin.getScheme() == null || origin.getHost() == null
				|| (!"http".equalsIgnoreCase(origin.getScheme())
						&& !"https".equalsIgnoreCase(origin.getScheme()))
				|| (origin.getPath() != null && !origin.getPath().isEmpty())
				|| origin.getQuery() != null || origin.getFragment() != null
				|| origin.getUserInfo() != null) {
			throw new IllegalArgumentException(
					"Notification allowed origin must be an HTTP(S) origin");
		}
	}
}
