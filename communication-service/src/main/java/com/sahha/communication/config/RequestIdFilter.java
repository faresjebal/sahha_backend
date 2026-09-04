package com.sahha.communication.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
	public static final String HEADER_NAME = "X-Request-ID";
	private static final String ATTRIBUTE_NAME = "sahha.requestId";
	private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,128}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String candidate = request.getHeader(HEADER_NAME);
		String requestId = candidate != null && SAFE.matcher(candidate).matches()
				? candidate : UUID.randomUUID().toString();
		request.setAttribute(ATTRIBUTE_NAME, requestId);
		response.setHeader(HEADER_NAME, requestId);
		MDC.put("requestId", requestId);
		try { chain.doFilter(request, response); }
		finally { MDC.remove("requestId"); }
	}

	public static String requestId(HttpServletRequest request) {
		Object value = request.getAttribute(ATTRIBUTE_NAME);
		return value instanceof String text ? text : "unavailable";
	}
}
