package com.sahha.notification.security;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.sahha.notification.exception.NotificationAccessDeniedException;
import com.sahha.notification.exception.OrganisationContextUnavailableException;
import com.sahha.notification.service.inappnotificationservice.NotificationAccessService;

@Component
public class NotificationWebSocketHandshakeInterceptor
		implements HandshakeInterceptor {

	private final NotificationAccessService accessService;

	public NotificationWebSocketHandshakeInterceptor(
			NotificationAccessService accessService) {
		this.accessService = accessService;
	}

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		if (!(request.getPrincipal() instanceof JwtAuthenticationToken authentication)
                || authentication.getAuthorities().stream().noneMatch(
                        authority -> NotificationPermissions.STREAM_SELF.equals(authority.getAuthority()))) {
			return reject(response, HttpStatus.UNAUTHORIZED);
		}
		try {
			accessService.requireActiveMembership(
					NotificationPrincipalName.organisationId(authentication.getToken()),
					NotificationPrincipalName.userId(authentication.getToken()),
					authentication.getToken().getTokenValue());
			return true;
		}
		catch (NotificationAccessDeniedException denied) {
			return reject(response, HttpStatus.FORBIDDEN);
		}
		catch (OrganisationContextUnavailableException unavailable) {
			return reject(response, HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception) {
		// No credentials or organisation data are retained in WebSocket attributes.
	}

	private static boolean reject(
			ServerHttpResponse response,
			HttpStatus status) {
		response.setStatusCode(status);
		response.getHeaders().setCacheControl(CacheControl.noStore());
		return false;
	}
}
