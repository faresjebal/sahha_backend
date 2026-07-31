package com.sahha.auth.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import com.sahha.auth.config.RequestIdFilter;

public final class AuthSecurityProblemWriter {

	private final ObjectMapper objectMapper;

	public AuthSecurityProblemWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void unauthorized(
			HttpServletRequest request,
			HttpServletResponse response)
			throws IOException {
		write(
				request,
				response,
				HttpStatus.UNAUTHORIZED,
				"Authentication required",
				"Authentication credentials are invalid or expired.",
				"urn:sahha:problem:authentication-required");
	}

	public void forbidden(
			HttpServletRequest request,
			HttpServletResponse response)
			throws IOException {
		write(
				request,
				response,
				HttpStatus.FORBIDDEN,
				"Request forbidden",
				"The request is not authorised.",
				"urn:sahha:problem:request-forbidden");
	}

	private void write(
			HttpServletRequest request,
			HttpServletResponse response,
			HttpStatus status,
			String title,
			String detail,
			String type)
			throws IOException {
		if (response.isCommitted()) {
			return;
		}
		Map<String, Object> problem = new LinkedHashMap<>();
		problem.put("type", type);
		problem.put("title", title);
		problem.put("status", status.value());
		problem.put("detail", detail);
		problem.put("instance", request.getRequestURI());
		problem.put("requestId", requestId(request));
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setHeader(
				HttpHeaders.CACHE_CONTROL,
				"no-store, no-cache, max-age=0");
		objectMapper.writeValue(response.getOutputStream(), problem);
	}

	private static String requestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
		return requestId instanceof String value
				? value
				: "unavailable";
	}
}
