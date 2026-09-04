package com.sahha.notification.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;

import com.sahha.notification.exception.NotificationAccessDeniedException;
import com.sahha.notification.service.inappnotificationservice.NotificationAccessService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketHandshakeInterceptorTests {

	@Mock
	private NotificationAccessService accessService;

	@Mock
	private ServerHttpRequest request;

	@Mock
	private ServerHttpResponse response;

	@Mock
	private WebSocketHandler webSocketHandler;

	private NotificationWebSocketHandshakeInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new NotificationWebSocketHandshakeInterceptor(accessService);
	}

	@Test
	void anonymousHandshakeIsRejectedBeforeMembershipLookup() {
		when(response.getHeaders()).thenReturn(new HttpHeaders());
		assertFalse(interceptor.beforeHandshake(
				request, response, webSocketHandler, Map.of()));

		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
		verifyNoInteractions(accessService);
	}

	@Test
	void inactiveMembershipIsRejectedDuringHandshake() {
		String tokenValue = "handshake.inactive.token";
		UUID userId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		JwtAuthenticationToken authentication = authentication(
				tokenValue, userId, organisationId);
		when(request.getPrincipal()).thenReturn(authentication);
		when(response.getHeaders()).thenReturn(new HttpHeaders());
		doThrow(new NotificationAccessDeniedException())
				.when(accessService)
				.requireActiveMembership(
						organisationId, userId, tokenValue);

		assertFalse(interceptor.beforeHandshake(
				request, response, webSocketHandler, Map.of()));

		verify(response).setStatusCode(HttpStatus.FORBIDDEN);
	}

	@Test
	void activeMembershipAllowsTheAuthenticatedHandshake() {
		String tokenValue = "handshake.active.token";
		UUID userId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		JwtAuthenticationToken authentication = authentication(
				tokenValue, userId, organisationId);
		when(request.getPrincipal()).thenReturn(authentication);

		assertTrue(interceptor.beforeHandshake(
				request, response, webSocketHandler, Map.of()));

		verify(accessService).requireActiveMembership(
				organisationId, userId, tokenValue);
	}

	private static JwtAuthenticationToken authentication(
			String tokenValue,
			UUID userId,
			UUID organisationId) {
		Instant now = Instant.now();
		Jwt jwt = Jwt.withTokenValue(tokenValue)
				.header("alg", "RS256")
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("roles", List.of())
				.claim("org_id", organisationId.toString())
				.claim("org_roles", List.of("DOCTOR"))
				.build();
		return (JwtAuthenticationToken)
				new NotificationJwtAuthenticationConverter().convert(jwt);
	}
}
