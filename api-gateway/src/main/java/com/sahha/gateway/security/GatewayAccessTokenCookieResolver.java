package com.sahha.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import com.sahha.gateway.config.GatewaySecurityProperties;

public final class GatewayAccessTokenCookieResolver
		implements BearerTokenResolver {

	private static final Pattern JWT_VALUE =
			Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
	private static final int MAXIMUM_JWT_LENGTH = 8_192;
	private static final Set<String> PUBLIC_AUTH_POST_PATHS = Set.of(
			"/api/v1/auth/registrations",
			"/api/v1/auth/email-verifications/confirm",
			"/api/v1/auth/email-verifications/resend",
			"/api/v1/auth/password-resets/request",
			"/api/v1/auth/password-resets/confirm",
			"/api/v1/auth/login",
			"/api/v1/auth/refresh");
	private static final OAuth2Error INVALID_REQUEST = new OAuth2Error(
			"invalid_request",
			"The access credential is malformed.",
			null);

	private final String cookieName;

	public GatewayAccessTokenCookieResolver(
			GatewaySecurityProperties properties) {
		this.cookieName = properties.accessTokenCookieName();
	}

	@Override
	public String resolve(HttpServletRequest request) {
		if (isPublicRequest(request)) {
			return null;
		}
		List<String> values = cookieValues(request.getCookies());
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

	private List<String> cookieValues(Cookie[] cookies) {
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

	private static boolean isPublicRequest(HttpServletRequest request) {
		if (HttpMethod.OPTIONS.matches(request.getMethod())) {
			return true;
		}
		String path = request.getRequestURI();
		if (path.startsWith("/actuator/health")
				|| path.equals("/.well-known/jwks.json")) {
			return true;
		}
		if (HttpMethod.GET.matches(request.getMethod())
				&& path.equals("/api/v1/auth/csrf")) {
			return true;
		}
		return HttpMethod.POST.matches(request.getMethod())
				&& PUBLIC_AUTH_POST_PATHS.contains(path);
	}
}
