package com.sahha.gateway.filter;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRequestContextFilter implements WebFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-ID";
	public static final String REQUEST_ID_ATTRIBUTE = "sahha.requestId";
	public static final String CLIENT_IP_HEADER = "X-Sahha-Client-IP";

	private static final Pattern SAFE_REQUEST_ID =
			Pattern.compile("^[A-Za-z0-9._-]{8,128}$");
	private static final Set<String> UNTRUSTED_HEADERS = Set.of(
			HttpHeaders.AUTHORIZATION,
			"Forwarded",
			"X-Forwarded-For",
			"X-Forwarded-Host",
			"X-Forwarded-Port",
			"X-Forwarded-Prefix",
			"X-Forwarded-Proto",
			CLIENT_IP_HEADER,
			"X-Sahha-User-Id",
			"X-Sahha-Session-Id",
			"X-Sahha-Platform-Roles",
			"X-Sahha-Active-Organisation-Id");

	@Override
	public Mono<Void> filter(
			ServerWebExchange exchange,
			WebFilterChain chain) {
		String requestId = resolveRequestId(
				exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER));
		String clientIp = clientIp(exchange.getRequest().getRemoteAddress());
		ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
				.headers(headers -> {
					UNTRUSTED_HEADERS.forEach(headers::remove);
					headers.set(REQUEST_ID_HEADER, requestId);
					headers.set(CLIENT_IP_HEADER, clientIp);
				})
				.build();
		ServerWebExchange sanitizedExchange = exchange.mutate()
				.request(sanitizedRequest)
				.build();
		sanitizedExchange.getAttributes().put(
				REQUEST_ID_ATTRIBUTE, requestId);
		sanitizedExchange.getResponse().getHeaders().set(
				REQUEST_ID_HEADER, requestId);
		sanitizedExchange.getResponse().beforeCommit(() -> {
			sanitizedExchange.getResponse().getHeaders().set(
					REQUEST_ID_HEADER, requestId);
			return Mono.empty();
		});
		return chain.filter(sanitizedExchange);
	}

	private static String resolveRequestId(String candidate) {
		return candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()
				? candidate
				: UUID.randomUUID().toString();
	}

	private static String clientIp(InetSocketAddress address) {
		return address == null || address.getAddress() == null
				? "unavailable"
				: address.getAddress().getHostAddress();
	}
}
