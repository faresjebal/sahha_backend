package com.sahha.file.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import com.sahha.file.config.FileSecurityProperties;

public final class FileAccessTokenCookieResolver implements BearerTokenResolver {

	private static final Pattern JWT_VALUE = Pattern.compile(
			"^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
	private static final OAuth2Error INVALID_REQUEST = new OAuth2Error(
			"invalid_request", "The access credential is malformed.", null);
	private final String cookieName;

	public FileAccessTokenCookieResolver(FileSecurityProperties properties) {
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
		if (values.size() != 1 || values.getFirst().length() > 8_192
				|| !JWT_VALUE.matcher(values.getFirst()).matches()) {
			throw new OAuth2AuthenticationException(INVALID_REQUEST);
		}
		return values.getFirst();
	}

	private List<String> cookieValues(Cookie[] cookies) {
		List<String> values = new ArrayList<>();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookieName.equals(cookie.getName())
						&& cookie.getValue() != null && !cookie.getValue().isBlank()) {
					values.add(cookie.getValue());
				}
			}
		}
		return values;
	}

	private static boolean isPublicRequest(HttpServletRequest request) {
		String path = request.getRequestURI();
		return HttpMethod.OPTIONS.matches(request.getMethod())
				|| path.startsWith("/actuator/health")
				|| path.startsWith("/v3/api-docs")
				|| path.equals("/swagger-ui.html")
				|| path.startsWith("/swagger-ui/");
	}
}
