package com.sahha.communication.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import com.sahha.communication.config.CommunicationSecurityProperties;

public final class CommunicationAccessTokenCookieResolver implements BearerTokenResolver {
	private static final Pattern JWT = Pattern.compile(
			"^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
	private final String cookieName;

	public CommunicationAccessTokenCookieResolver(CommunicationSecurityProperties properties) {
		cookieName = properties.accessTokenCookieName();
	}

	@Override
	public String resolve(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (HttpMethod.OPTIONS.matches(request.getMethod())
				|| path.startsWith("/actuator/health") || path.startsWith("/v3/api-docs")
				|| path.equals("/swagger-ui.html") || path.startsWith("/swagger-ui/")) {
			return null;
		}
		List<String> values = new ArrayList<>();
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (cookieName.equals(cookie.getName()) && cookie.getValue() != null
						&& !cookie.getValue().isBlank()) values.add(cookie.getValue());
			}
		}
		if (values.isEmpty()) return null;
		if (values.size() != 1 || values.getFirst().length() > 8192
				|| !JWT.matcher(values.getFirst()).matches()) {
			throw new OAuth2AuthenticationException(new OAuth2Error(
					"invalid_request", "The access credential is malformed.", null));
		}
		return values.getFirst();
	}
}
