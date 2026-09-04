package com.sahha.communication.security;

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

public final class CookieAuthenticatedCsrfFilter extends OncePerRequestFilter {
	private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
	private final String accessCookie;
	private final String csrfCookie;
	private final String csrfHeader;
	private final CommunicationSecurityProblemWriter problemWriter;

	public CookieAuthenticatedCsrfFilter(String accessCookie, String csrfCookie,
			String csrfHeader, CommunicationSecurityProblemWriter problemWriter) {
		this.accessCookie = accessCookie;
		this.csrfCookie = csrfCookie;
		this.csrfHeader = csrfHeader;
		this.problemWriter = problemWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (SAFE.contains(request.getMethod()) || cookie(request, accessCookie) == null) {
			chain.doFilter(request, response);
			return;
		}
		String expected = cookie(request, csrfCookie);
		String actual = request.getHeader(csrfHeader);
		if (expected == null || actual == null || !MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
			problemWriter.forbidden(request, response);
			return;
		}
		chain.doFilter(request, response);
	}

	private static String cookie(HttpServletRequest request, String name) {
		if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
			if (name.equals(cookie.getName())) return cookie.getValue();
		}
		return null;
	}
}
