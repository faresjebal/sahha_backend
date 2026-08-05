package com.sahha.auth.service.browsersessionservice;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;

import com.sahha.auth.config.AuthCookieProperties;
import com.sahha.auth.security.SecureTokenGenerator;
import com.sahha.auth.service.accesstokenservice.IssuedAccessToken;
import com.sahha.auth.service.usersessionservice.IssuedBrowserSession;

@Service
public class BrowserSessionCookieService {

	private static final Pattern DEVICE_ID =
			Pattern.compile("^[A-Za-z0-9_-]{32,128}$");

	private final AuthCookieProperties properties;
	private final SecureTokenGenerator tokenGenerator;
	private final CookieCsrfTokenRepository csrfTokenRepository;
	private final Clock clock;

	public BrowserSessionCookieService(
			AuthCookieProperties properties,
			SecureTokenGenerator tokenGenerator,
			CookieCsrfTokenRepository csrfTokenRepository,
			Clock clock) {
		this.properties = properties;
		this.tokenGenerator = tokenGenerator;
		this.csrfTokenRepository = csrfTokenRepository;
		this.clock = clock;
	}

	public String resolveOrCreateDeviceId(HttpServletRequest request) {
		return singleCookie(request, properties.deviceIdName())
				.filter(value -> DEVICE_ID.matcher(value).matches())
				.orElseGet(tokenGenerator::generate);
	}

	public Optional<String> refreshToken(HttpServletRequest request) {
		return singleCookie(request, properties.refreshTokenName());
	}

	public void writeAuthenticatedSession(
			HttpServletResponse response,
			IssuedBrowserSession session,
			String rawDeviceId) {
		Instant now = clock.instant();
		add(
				response,
				authCookie(
						properties.accessTokenName(),
						session.getRawAccessToken(),
						properties.accessPath(),
						remaining(now, session.getAccessTokenExpiresAt())));
		add(
				response,
				authCookie(
						properties.refreshTokenName(),
						session.getRawRefreshToken(),
						properties.refreshPath(),
						remaining(now, session.getRefreshTokenExpiresAt())));
		add(
				response,
				authCookie(
						properties.deviceIdName(),
						rawDeviceId,
						properties.refreshPath(),
						properties.deviceIdLifetime()));
		noStore(response);
	}

	public void writeAccessToken(
			HttpServletResponse response,
			IssuedAccessToken accessToken) {
		Instant now = clock.instant();
		add(
				response,
				authCookie(
						properties.accessTokenName(),
						accessToken.getRawToken(),
						properties.accessPath(),
						remaining(now, accessToken.getExpiresAt())));
		noStore(response);
	}

	public void clearAuthentication(HttpServletResponse response) {
		add(
				response,
				authCookie(
						properties.accessTokenName(),
						"",
						properties.accessPath(),
						Duration.ZERO));
		add(
				response,
				authCookie(
						properties.refreshTokenName(),
						"",
						properties.refreshPath(),
						Duration.ZERO));
		noStore(response);
	}

	public CsrfToken rotateCsrf(
			HttpServletRequest request,
			HttpServletResponse response) {
		CsrfToken token = csrfTokenRepository.generateToken(request);
		csrfTokenRepository.saveToken(token, request, response);
		return token;
	}

	private Optional<String> singleCookie(
			HttpServletRequest request,
			String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		List<String> values = new ArrayList<>();
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())
					&& cookie.getValue() != null
					&& !cookie.getValue().isBlank()) {
				values.add(cookie.getValue());
			}
		}
		return values.size() == 1
				? Optional.of(values.getFirst())
				: Optional.empty();
	}

	private ResponseCookie authCookie(
			String name,
			String value,
			String path,
			Duration maxAge) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(properties.secure())
				.sameSite(properties.sameSite())
				.path(path)
				.maxAge(maxAge)
				.build();
	}

	private static Duration remaining(Instant now, Instant expiresAt) {
		if (!expiresAt.isAfter(now)) {
			return Duration.ZERO;
		}
		return Duration.between(now, expiresAt);
	}

	private static void add(
			HttpServletResponse response,
			ResponseCookie cookie) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	private static void noStore(HttpServletResponse response) {
		response.setHeader(
				HttpHeaders.CACHE_CONTROL,
				"no-store, no-cache, max-age=0");
		response.setHeader(HttpHeaders.PRAGMA, "no-cache");
	}
}
