package com.sahha.communication.integration;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.sahha.communication.client.organisation.CollaborationDoctorResource;
import com.sahha.communication.client.organisation.OrganisationCollaborationClient;
import com.sahha.communication.config.CommunicationWebSocketConfiguration;
import com.sahha.communication.security.CommunicationPrincipalName;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sahha.communication.security.access-token-cookie-name=SAHHA_TEST_ACCESS",
        "sahha.communication.security.csrf-token-name=SAHHA_TEST_XSRF",
        "sahha.communication.websocket.allowed-origins=http://127.0.0.1:5173"
})
class CommunicationWebSocketIntegrationTests {
    private static final String TOKEN = "synthetic.websocket.token", CSRF = "synthetic-raw-csrf";
    private static final String ORIGIN = "http://127.0.0.1:5173";
    @LocalServerPort int port;
    @Autowired SimpUserRegistry registry;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean OrganisationCollaborationClient organisationClient;
    private final UUID organisationId = UUID.randomUUID(), userId = UUID.randomUUID();
    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient client;

    @BeforeEach void prepare() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.start();
        workspace(List.of("DOCTOR"));
        when(organisationClient.resolve(organisationId, userId, TOKEN)).thenReturn(
                new CollaborationDoctorResource(UUID.randomUUID(), organisationId, userId, "Dr Synthetic", 0));
    }
    @AfterEach void stop() {
        try { for (var session : sessions) if (session.isConnected()) session.disconnect(); }
        finally { client.stop(); }
    }
    @Test void rawCookieBoundCsrfConnectsAndSubscribesUnderTheScopedPrincipal() throws Exception {
        var session = connect(CSRF, ORIGIN).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        session.subscribe(CommunicationWebSocketConfiguration.USER_DESTINATION, handler());
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            var user = registry.getUser(CommunicationPrincipalName.of(userId, organisationId));
            return user != null && user.getSessions().stream().flatMap(value -> value.getSubscriptions().stream())
                    .anyMatch(value -> CommunicationWebSocketConfiguration.USER_DESTINATION.equals(value.getDestination()));
        });
        assertTrue(session.isConnected());
    }
    @Test void missingOrMismatchedCsrfStillRejectsConnect() {
        for (String value : new String[]{null, "wrong-synthetic-token"}) {
            assertThrows(ExecutionException.class, () -> connect(value, ORIGIN).get(5, TimeUnit.SECONDS));
        }
    }
    @Test void foreignAndUnconfiguredLocalOriginsStayDenied() {
        for (String origin : List.of("https://untrusted.example.test", "http://localhost:5173")) {
            assertThrows(ExecutionException.class, () -> connect(CSRF, origin).get(5, TimeUnit.SECONDS));
        }
    }
    @Test void receptionistAndLostMembershipCannotOpenTheStream() {
        workspace(List.of("RECEPTIONIST"));
        assertThrows(ExecutionException.class, () -> connect(CSRF, ORIGIN).get(5, TimeUnit.SECONDS));
        workspace(List.of("DOCTOR"));
        when(organisationClient.resolve(organisationId, userId, TOKEN))
                .thenThrow(new IllegalStateException("Synthetic inactive membership"));
        assertThrows(ExecutionException.class, () -> connect(CSRF, ORIGIN).get(5, TimeUnit.SECONDS));
    }
    @Test void arbitraryQueueSubscriptionsRemainForbidden() throws Exception {
        var session = connect(CSRF, ORIGIN).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        session.subscribe("/queue/messages", handler());
        await().atMost(5, TimeUnit.SECONDS).until(() -> !session.isConnected());
    }
    private CompletableFuture<StompSession> connect(String csrf, String origin) {
        var handshake = new WebSocketHttpHeaders();
        handshake.add(HttpHeaders.ORIGIN, origin);
        handshake.add(HttpHeaders.COOKIE, "SAHHA_TEST_ACCESS=" + TOKEN + "; SAHHA_TEST_XSRF=" + CSRF);
        var headers = new StompHeaders();
        if (csrf != null) headers.add("X-XSRF-TOKEN", csrf);
        return client.connectAsync(URI.create("ws://127.0.0.1:" + port + CommunicationWebSocketConfiguration.ENDPOINT),
                handshake, headers, new StompSessionHandlerAdapter() {});
    }
    private void workspace(List<String> roles) {
        var now = Instant.now();
        when(decoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "RS256")
                .subject(userId.toString()).issuer("http://localhost:8081").audience(List.of("sahha-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("sid", UUID.randomUUID().toString())
                .claim("cv", 1).claim("roles", List.of()).claim("org_id", organisationId.toString())
                .claim("org_roles", roles).claim("token_type", "access").build());
    }
    private static StompFrameHandler handler() {
        return new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            public void handleFrame(StompHeaders headers, Object payload) { fail("No message was published in this test"); }
        };
    }
}
