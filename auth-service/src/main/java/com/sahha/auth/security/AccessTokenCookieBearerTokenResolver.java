package com.sahha.auth.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import com.sahha.auth.config.AuthCookieProperties;

public final class AccessTokenCookieBearerTokenResolver
		implements BearerTokenResolver {

	private static final Pattern JWT_VALUE =
			Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
	private static final int MAXIMUM_JWT_LENGTH = 8_192;
	private static final OAuth2Error INVALID_REQUEST = new OAuth2Error(
			"invalid_request",
			"The access credential is malformed.",
			null);

	private final String cookieName;

	public AccessTokenCookieBearerTokenResolver(
			AuthCookieProperties properties) {
		cookieName = Objects.requireNonNull(
				properties,
				"properties must not be null")
				.accessTokenName();
	}

	@Override
	public String resolve(HttpServletRequest request) {
		if (shouldIgnore(request)) {
			return null;
		}
		List<String> values = accessCookieValues(request.getCookies());
		if (values.isEmpty()) {
			return null;
		}
		if (values.size() != 1) {
			throw new OAuth2AuthenticationException(INVALID_REQUEST);
		}
		String value = values.getFirst();
		if (value.length() > MAXIMUM_JWT_LENGTH
				|| !JWT_VALUE.matcher(value).matches()) {
			throw new OAuth2AuthenticationException(INVALID_REQUEST);
		}
		return value;
	}

	private List<String> accessCookieValues(Cookie[] cookies) {
		List<String> values = new ArrayList<>();
		if (cookies == null) {
			return values;
		}
		for (Cookie cookie : cookies) {
			if (cookieName.equals(cookie.getName())
					&& cookie.getValue() != null
					&& !cookie.getValue().isBlank()) {
				values.add(cookie.getValue());
			}
		}
		return values;
	}

	private static boolean shouldIgnore(HttpServletRequest request) {
		if (HttpMethod.OPTIONS.matches(request.getMethod())) {
			return true;
		}
		String path = request.getRequestURI();
		if (path.startsWith("/actuator/health")
				|| path.startsWith("/v3/api-docs")
				|| path.startsWith("/swagger-ui")
				|| path.equals("/.well-known/jwks.json")) {
			return true;
		}
		if (!path.startsWith("/api/v1/auth/")) {
			return false;
		}
		return path.equals("/api/v1/auth/csrf")
				|| path.equals("/api/v1/auth/registrations")
				|| path.equals(
						"/api/v1/auth/email-verifications/confirm")
				|| path.equals(
						"/api/v1/auth/email-verifications/resend")
				|| path.equals("/api/v1/auth/password-resets/request")
				|| path.equals("/api/v1/auth/password-resets/confirm")
				|| path.equals("/api/v1/auth/login")
				|| path.equals("/api/v1/auth/refresh");
	}
}
