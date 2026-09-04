package com.sahha.communication.security;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.sahha.communication.client.organisation.OrganisationCollaborationClient;

@Component
public class CommunicationWebSocketHandshakeInterceptor implements HandshakeInterceptor {
	private final OrganisationCollaborationClient organisationClient;
	public CommunicationWebSocketHandshakeInterceptor(
			OrganisationCollaborationClient organisationClient) {
		this.organisationClient = organisationClient;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request,
			ServerHttpResponse response, WebSocketHandler handler,
			Map<String, Object> attributes) {
		if (!(request.getPrincipal() instanceof JwtAuthenticationToken authentication)
				|| !authentication.getAuthorities().stream().anyMatch(
						value -> "ROLE_DOCTOR".equals(value.getAuthority()))) {
			return reject(response, HttpStatus.UNAUTHORIZED);
		}
		try {
			JwtAuthenticationToken token = authentication;
			var jwt = token.getToken();
			organisationClient.resolve(
					java.util.UUID.fromString(jwt.getClaimAsString(
							CommunicationAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM)),
					java.util.UUID.fromString(jwt.getSubject()), jwt.getTokenValue());
			return true;
		}
		catch (RuntimeException denied) {
			return reject(response, HttpStatus.FORBIDDEN);
		}
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler handler, Exception exception) { }

	private static boolean reject(ServerHttpResponse response, HttpStatus status) {
		response.setStatusCode(status);
		response.getHeaders().setCacheControl(CacheControl.noStore());
		return false;
	}
}
