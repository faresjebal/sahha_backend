package com.sahha.communication.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import com.sahha.communication.config.RequestIdFilter;

public final class CommunicationSecurityProblemWriter {
	private final ObjectMapper objectMapper;
	public CommunicationSecurityProblemWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}
	public void unauthorized(HttpServletRequest request, HttpServletResponse response)
			throws IOException { write(request, response, HttpStatus.UNAUTHORIZED,
			"Authentication required", "Authentication credentials are invalid or expired.",
			"urn:sahha:problem:authentication-required"); }
	public void forbidden(HttpServletRequest request, HttpServletResponse response)
			throws IOException { write(request, response, HttpStatus.FORBIDDEN,
			"Request forbidden", "The request is not authorised.",
			"urn:sahha:problem:request-forbidden"); }
	private void write(HttpServletRequest request, HttpServletResponse response,
			HttpStatus status, String title, String detail, String type) throws IOException {
		if (response.isCommitted()) return;
		Map<String,Object> body = new LinkedHashMap<>();
		body.put("type", type); body.put("title", title); body.put("status", status.value());
		body.put("detail", detail); body.put("instance", request.getRequestURI());
		body.put("requestId", RequestIdFilter.requestId(request));
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
		objectMapper.writeValue(response.getOutputStream(), body);
	}
}
