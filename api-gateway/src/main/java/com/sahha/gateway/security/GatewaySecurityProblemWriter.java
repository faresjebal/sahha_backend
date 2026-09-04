package com.sahha.gateway.security;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import com.sahha.gateway.filter.GatewayRequestContextFilter;

public final class GatewaySecurityProblemWriter {

	private final ObjectMapper objectMapper;

	public GatewaySecurityProblemWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Mono<Void> unauthorized(ServerWebExchange exchange) {
		exchange.getResponse().getHeaders().set(
				HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		return write(
				exchange,
				HttpStatus.UNAUTHORIZED,
				"Authentication required",
				"Authentication credentials are invalid or expired.",
				"urn:sahha:problem:authentication-required");
	}

	public Mono<Void> forbidden(ServerWebExchange exchange) {
		return write(
				exchange,
				HttpStatus.FORBIDDEN,
				"Request forbidden",
				"The request is not authorised.",
				"urn:sahha:problem:request-forbidden");
	}

	private Mono<Void> write(
			ServerWebExchange exchange,
			HttpStatus status,
			String title,
			String detail,
			String type) {
		if (exchange.getResponse().isCommitted()) {
			return Mono.empty();
		}
		Map<String, Object> problem = new LinkedHashMap<>();
		problem.put("type", type);
		problem.put("title", title);
		problem.put("status", status.value());
		problem.put("detail", detail);
		problem.put("instance", exchange.getRequest().getPath().value());
		problem.put("requestId", exchange.getAttributeOrDefault(
				GatewayRequestContextFilter.REQUEST_ID_ATTRIBUTE,
				"unavailable"));
		exchange.getResponse().setStatusCode(status);
		exchange.getResponse().getHeaders().setContentType(
				MediaType.APPLICATION_PROBLEM_JSON);
		exchange.getResponse().getHeaders().set(
				HttpHeaders.CACHE_CONTROL,
				"no-store, no-cache, max-age=0");
		byte[] body = objectMapper.writeValueAsBytes(problem);
		DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}
}
