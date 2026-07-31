package com.sahha.organisation.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CookieAuthenticatedCsrfFilter
		extends OncePerRequestFilter {

	private static final Set<String> SAFE_METHODS =
			Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	private final String accessCookieName;
	private final String csrfCookieName;
	private final String csrfHeaderName;
	private final OrganisationSecurityProblemWriter problemWriter;

	public CookieAuthenticatedCsrfFilter(
			String accessCookieName,
			String csrfCookieName,
			String csrfHeaderName,
			OrganisationSecurityProblemWriter problemWriter) {
		this.accessCookieName = accessCookieName;
		this.csrfCookieName = csrfCookieName;
		this.csrfHeaderName = csrfHeaderName;
		this.problemWriter = problemWriter;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain)
			throws ServletException, IOException {
		if (SAFE_METHODS.contains(request.getMethod())
				|| cookieValue(request, accessCookieName) == null) {
			filterChain.doFilter(request, response);
			return;
		}
		String cookieToken = cookieValue(request, csrfCookieName);
		String headerToken = request.getHeader(csrfHeaderName);
		if (!matches(cookieToken, headerToken)) {
			problemWriter.forbidden(request, response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private static String cookieValue(
			HttpServletRequest request,
			String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private static boolean matches(String expected, String actual) {
		if (expected == null
				|| expected.isBlank()
				|| actual == null
				|| actual.isBlank()) {
			return false;
		}
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				actual.getBytes(StandardCharsets.UTF_8));
	}
}
