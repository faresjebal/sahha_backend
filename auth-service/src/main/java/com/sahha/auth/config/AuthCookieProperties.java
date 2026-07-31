package com.sahha.auth.config;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.auth.cookies")
public record AuthCookieProperties(
		@DefaultValue("SAHHA_ACCESS_TOKEN") String accessTokenName,
		@DefaultValue("SAHHA_REFRESH_TOKEN") String refreshTokenName,
		@DefaultValue("SAHHA_DEVICE_ID") String deviceIdName,
		@DefaultValue("XSRF-TOKEN") String csrfTokenName,
		@DefaultValue("X-XSRF-TOKEN") String csrfHeaderName,
		@DefaultValue("/") String accessPath,
		@DefaultValue("/api/v1/auth") String refreshPath,
		@DefaultValue("Lax") String sameSite,
		@DefaultValue("true") boolean secure,
		@DefaultValue("P365D") Duration deviceIdLifetime) {

	private static final Set<String> ALLOWED_SAME_SITE =
			Set.of("Strict", "Lax", "None");

	public AuthCookieProperties {
		accessTokenName = requireToken(accessTokenName, "accessTokenName");
		refreshTokenName = requireToken(refreshTokenName, "refreshTokenName");
		deviceIdName = requireToken(deviceIdName, "deviceIdName");
		csrfTokenName = requireToken(csrfTokenName, "csrfTokenName");
		csrfHeaderName = requireToken(csrfHeaderName, "csrfHeaderName");
		accessPath = requirePath(accessPath, "accessPath");
		refreshPath = requirePath(refreshPath, "refreshPath");
		sameSite = normalizeSameSite(sameSite);
		if ("None".equals(sameSite) && !secure) {
			throw new IllegalArgumentException(
					"SameSite=None requires secure cookies");
		}
		if (deviceIdLifetime == null
				|| deviceIdLifetime.isZero()
				|| deviceIdLifetime.isNegative()) {
			throw new IllegalArgumentException(
					"deviceIdLifetime must be positive");
		}
	}

	private static String requireToken(String value, String fieldName) {
		if (value == null
				|| value.isBlank()
				|| !value.matches("^[A-Za-z0-9_-]+$")) {
			throw new IllegalArgumentException(
					fieldName + " must be a safe non-blank token");
		}
		return value;
	}

	private static String requirePath(String value, String fieldName) {
		if (value == null || !value.startsWith("/")) {
			throw new IllegalArgumentException(
					fieldName + " must be an absolute HTTP path");
		}
		return value;
	}

	private static String normalizeSameSite(String value) {
		if (value == null) {
			throw new IllegalArgumentException("sameSite must not be null");
		}
		for (String candidate : ALLOWED_SAME_SITE) {
			if (candidate.equalsIgnoreCase(value.strip())) {
				return candidate;
			}
		}
		throw new IllegalArgumentException(
				"sameSite must be Strict, Lax, or None");
	}
}
