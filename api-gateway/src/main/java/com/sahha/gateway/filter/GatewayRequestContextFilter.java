package com.sahha.gateway.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRequestContextFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-ID";
	public static final String REQUEST_ID_ATTRIBUTE = "sahha.requestId";
	public static final String CLIENT_IP_HEADER = "X-Sahha-Client-IP";

	private static final Pattern SAFE_REQUEST_ID =
			Pattern.compile("^[A-Za-z0-9._-]{8,128}$");
	private static final Set<String> UNTRUSTED_HEADERS = Set.of(
			"authorization",
			"forwarded",
			"x-forwarded-for",
			"x-forwarded-host",
			"x-forwarded-port",
			"x-forwarded-prefix",
			"x-forwarded-proto",
			"x-sahha-client-ip",
			"x-sahha-user-id",
			"x-sahha-session-id",
			"x-sahha-platform-roles",
			"x-sahha-active-organisation-id");

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = resolveRequestId(
				request.getHeader(REQUEST_ID_HEADER));
		request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		MDC.put("requestId", requestId);
		try {
			filterChain.doFilter(
					new SanitizedHeaderRequest(request, requestId),
					response);
		}
		finally {
			MDC.remove("requestId");
		}
	}

	private static String resolveRequestId(String candidate) {
		if (candidate != null
				&& SAFE_REQUEST_ID.matcher(candidate).matches()) {
			return candidate;
		}
		return UUID.randomUUID().toString();
	}

	private static final class SanitizedHeaderRequest
			extends HttpServletRequestWrapper {

		private final String requestId;
		private final String clientIp;

		private SanitizedHeaderRequest(
				HttpServletRequest request,
				String requestId) {
			super(request);
			this.requestId = requestId;
			this.clientIp = request.getRemoteAddr();
		}

		@Override
		public String getHeader(String name) {
			if (REQUEST_ID_HEADER.equalsIgnoreCase(name)) {
				return requestId;
			}
			if (CLIENT_IP_HEADER.equalsIgnoreCase(name)) {
				return clientIp;
			}
			if (isUntrusted(name)) {
				return null;
			}
			return super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			if (REQUEST_ID_HEADER.equalsIgnoreCase(name)) {
				return Collections.enumeration(Set.of(requestId));
			}
			if (CLIENT_IP_HEADER.equalsIgnoreCase(name)) {
				return Collections.enumeration(Set.of(clientIp));
			}
			if (isUntrusted(name)) {
				return Collections.emptyEnumeration();
			}
			return super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			LinkedHashSet<String> names = new LinkedHashSet<>();
			Enumeration<String> original = super.getHeaderNames();
			if (original != null) {
				while (original.hasMoreElements()) {
					String name = original.nextElement();
					if (!REQUEST_ID_HEADER.equalsIgnoreCase(name)
							&& !isUntrusted(name)) {
						names.add(name);
					}
				}
			}
			names.add(REQUEST_ID_HEADER);
			names.add(CLIENT_IP_HEADER);
			return Collections.enumeration(names);
		}

		private static boolean isUntrusted(String name) {
			return name != null
					&& UNTRUSTED_HEADERS.contains(
							name.toLowerCase(Locale.ROOT));
		}
	}
}
