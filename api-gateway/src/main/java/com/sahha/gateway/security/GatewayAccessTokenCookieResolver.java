package com.sahha.gateway.security;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.sahha.gateway.config.GatewaySecurityProperties;

public final class GatewayAccessTokenCookieResolver
		implements ServerAuthenticationConverter {

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
	public Mono<Authentication> convert(ServerWebExchange exchange) {
		if (isPublicRequest(exchange)) {
			return Mono.empty();
		}
		List<HttpCookie> values = exchange.getRequest().getCookies().get(cookieName);
		if (values == null || values.isEmpty()) {
			return Mono.empty();
		}
		if (values.size() != 1) {
			return invalid();
		}
		String value = values.getFirst().getValue();
		if (value == null || value.isBlank()
				|| value.length() > MAXIMUM_JWT_LENGTH
				|| !JWT_VALUE.matcher(value).matches()) {
			return invalid();
		}
		return Mono.just(new BearerTokenAuthenticationToken(value));
	}

	private static Mono<Authentication> invalid() {
		return Mono.error(new OAuth2AuthenticationException(INVALID_REQUEST));
	}

	private static boolean isPublicRequest(ServerWebExchange exchange) {
		HttpMethod method = exchange.getRequest().getMethod();
		if (HttpMethod.OPTIONS.equals(method)) {
			return true;
		}
		String path = exchange.getRequest().getPath().value();
		if (path.startsWith("/actuator/health")
				|| path.equals("/.well-known/jwks.json")) {
			return true;
		}
		if (HttpMethod.GET.equals(method)
				&& path.equals("/api/v1/auth/csrf")) {
			return true;
		}
		return HttpMethod.POST.equals(method)
				&& PUBLIC_AUTH_POST_PATHS.contains(path);
	}
}
