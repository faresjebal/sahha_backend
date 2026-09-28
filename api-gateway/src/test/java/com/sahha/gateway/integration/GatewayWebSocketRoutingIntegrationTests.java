package com.sahha.gateway.integration;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayWebSocketRoutingIntegrationTests {

	private static final String ACCESS_TOKEN = "gateway.websocket.token";
	private static final DisposableServer NOTIFICATION_WEBSOCKET_SERVER =
			startNotificationWebSocketServer();

	@LocalServerPort
	private int gatewayPort;

	@MockitoBean
	private ReactiveJwtDecoder jwtDecoder;

	@DynamicPropertySource
	static void notificationServiceInstance(
			DynamicPropertyRegistry registry) {
		registry.add(
				"spring.cloud.discovery.client.simple.instances.notification-service[0].uri",
				() -> "http://127.0.0.1:"
						+ NOTIFICATION_WEBSOCKET_SERVER.port());
	}

	@AfterAll
	static void stopNotificationWebSocketServer() {
		NOTIFICATION_WEBSOCKET_SERVER.disposeNow();
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"/api/v1/notifications/ws", "/api/v1/notifications/patient/ws?registrationId=synthetic"})
	void authenticatedBrowserWebSocketIsUpgradedAndProxied(String endpoint) {
		when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(Mono.just(jwt()));
		String outboundMessage = "synthetic-realtime-proxy-check";
		AtomicReference<String> receivedMessage = new AtomicReference<>();
		HttpHeaders headers = new HttpHeaders();
		headers.add(
				HttpHeaders.COOKIE,
				"SAHHA_ACCESS_TOKEN=" + ACCESS_TOKEN);
		headers.add(HttpHeaders.ORIGIN, "http://localhost:5173");

		new ReactorNettyWebSocketClient().execute(
				URI.create("ws://127.0.0.1:" + gatewayPort
						+ endpoint),
				headers,
				session -> session
						.send(Mono.just(session.textMessage(outboundMessage)))
						.thenMany(session.receive().take(1))
						.doOnNext(message -> receivedMessage.set(
								message.getPayloadAsText()))
						.then())
				.block(Duration.ofSeconds(5));

		assertEquals(outboundMessage, receivedMessage.get());
	}

	private static DisposableServer startNotificationWebSocketServer() {
		return HttpServer.create()
				.host("127.0.0.1")
				.port(0)
				.route(routes -> routes.ws(
						"/api/v1/notifications/ws",
						(inbound, outbound) -> outbound.send(
								inbound.receive().retain()))
						.ws("/api/v1/notifications/patient/ws", (inbound, outbound) -> outbound.send(inbound.receive().retain())))
				.bindNow();
	}

	private static Jwt jwt() {
		Instant now = Instant.now();
		return Jwt.withTokenValue(ACCESS_TOKEN)
				.header("alg", "RS256")
				.subject(UUID.randomUUID().toString())
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of())
				.claim("org_id", UUID.randomUUID().toString())
				.claim("org_roles", List.of("DOCTOR"))
				.claim("token_type", "access")
				.build();
	}
}
