package com.sahha.file.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.file.security")
public record FileSecurityProperties(
		@DefaultValue("SAHHA_ACCESS_TOKEN") String accessTokenCookieName,
		@DefaultValue("http://localhost:8081/.well-known/jwks.json") URI jwkSetUri,
		@DefaultValue("http://localhost:8081") String issuer,
		@DefaultValue("sahha-api") String audience,
		@DefaultValue("XSRF-TOKEN") String csrfTokenName,
		@DefaultValue("X-XSRF-TOKEN") String csrfHeaderName,
		@DefaultValue("false") boolean secureCookies) {

	public FileSecurityProperties {
		if (accessTokenCookieName == null || accessTokenCookieName.isBlank()
				|| csrfTokenName == null || csrfTokenName.isBlank()
				|| csrfHeaderName == null || csrfHeaderName.isBlank()
				|| issuer == null || issuer.isBlank()
				|| audience == null || audience.isBlank()
				|| jwkSetUri == null || jwkSetUri.getScheme() == null) {
			throw new IllegalArgumentException("File security properties are invalid");
		}
	}
}
