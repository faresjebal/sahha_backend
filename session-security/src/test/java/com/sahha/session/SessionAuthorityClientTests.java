package com.sahha.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

class SessionAuthorityClientTests {
    private static final String TOKEN = "synthetic.payload.signature";
    private HttpServer server;
    private SessionAuthorityClient client;
    private final AtomicInteger status = new AtomicInteger(204);
    private final AtomicReference<String> echo = new AtomicReference<>();
    private final List<String> challenges = new CopyOnWriteArrayList<>();
    private final List<String> cookies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/check", exchange -> {
            String challenge = exchange.getRequestHeaders().getFirst(SessionAuthorityClient.CHALLENGE_HEADER);
            challenges.add(challenge);
            cookies.add(exchange.getRequestHeaders().getFirst("Cookie"));
            assertEquals("GET", exchange.getRequestMethod());
            assertEquals("no-store", exchange.getRequestHeaders().getFirst("Cache-Control"));
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            if (!"missing".equals(echo.get())) {
                exchange.getResponseHeaders().set(SessionAuthorityClient.CHALLENGE_HEADER,
                        echo.get() == null ? challenge : echo.get());
            }
            exchange.getResponseHeaders().set("Location", "/check");
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        client = new SessionAuthorityClient(uri("/check"), "SAHHA_ACCESS_TOKEN", Duration.ofSeconds(1));
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    @Test
    void checksEveryRequestWithFreshChallengeAndOnlyTheAccessCookie() {
        client.requireActive(TOKEN);
        client.requireActive(TOKEN);
        assertEquals(List.of("SAHHA_ACCESS_TOKEN=" + TOKEN, "SAHHA_ACCESS_TOKEN=" + TOKEN), cookies);
        assertEquals(2, challenges.size());
        assertNotEquals(challenges.get(0), challenges.get(1));
        status.set(401);
        assertThrows(BadJwtException.class, () -> client.requireActive(TOKEN));
        assertEquals(3, challenges.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 301, 302, 307, 401, 403, 404, 500, 503})
    void deniesNonContractResponsesWithoutFollowingRedirects(int responseStatus) {
        status.set(responseStatus);
        assertThrows(BadJwtException.class, () -> client.requireActive(TOKEN));
        assertEquals(1, challenges.size());
    }

    @Test
    void rejectsMissingOrReplayedChallenge() {
        client.requireActive(TOKEN);
        echo.set(challenges.getFirst());
        assertThrows(BadJwtException.class, () -> client.requireActive(TOKEN));
        echo.set("missing");
        assertThrows(BadJwtException.class, () -> client.requireActive(TOKEN));
    }

    @Test
    void rejectsCookieInjectionBeforeAnyNetworkCall() {
        assertThrows(BadJwtException.class, () -> client.requireActive(TOKEN + "; OTHER=secret"));
        assertThrows(BadJwtException.class, () -> client.requireActive(TOKEN + "\r\nX-Test: injected"));
        assertThrows(BadJwtException.class, () -> client.requireActive(null));
        assertTrue(challenges.isEmpty());
    }

    @Test
    void rejectsUnavailableAuthority() {
        server.stop(0);
        assertThrows(BadJwtException.class, () -> client.requireActive(TOKEN));
    }

    @Test
    void boundsAnUnresponsiveAuthority() {
        server.createContext("/hang", exchange -> { /* intentionally never respond */ });
        try (var bounded = new SessionAuthorityClient(uri("/hang"), "ACCESS", Duration.ofMillis(150))) {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThrows(BadJwtException.class, () -> bounded.requireActive(TOKEN)));
        }
    }

    @Test
    void doesNotWaitForAnUnboundedErrorResponseBody() {
        server.createContext("/stream", exchange -> exchange.sendResponseHeaders(503, 0));
        try (var bounded = new SessionAuthorityClient(uri("/stream"), "ACCESS", Duration.ofMillis(150))) {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThrows(BadJwtException.class, () -> bounded.requireActive(TOKEN)));
        }
    }

    @Test
    void synchronousDecoderValidatesLocallyBeforeAskingAuth() {
        JwtDecoder local = mock(JwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "RS256").subject("synthetic").build();
        when(local.decode(TOKEN)).thenReturn(jwt);
        var decoder = new SessionCheckingJwtDecoder(local, client);
        assertSame(jwt, decoder.decode(TOKEN));
        status.set(401);
        assertThrows(BadJwtException.class, () -> decoder.decode(TOKEN));
        when(local.decode("invalid")).thenThrow(new BadJwtException("invalid"));
        assertThrows(BadJwtException.class, () -> decoder.decode("invalid"));
        assertEquals(2, challenges.size());
    }

    @Test
    void reactiveDecoderDefersIoAndRejectsRevocationWithoutBlockingItsCaller() {
        ReactiveJwtDecoder local = mock(ReactiveJwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "RS256").subject("synthetic").build();
        when(local.decode(TOKEN)).thenReturn(Mono.just(jwt));
        var decoder = new SessionCheckingReactiveJwtDecoder(local, client);
        Mono<Jwt> result = decoder.decode(TOKEN);
        assertTrue(challenges.isEmpty());
        assertSame(jwt, result.block(Duration.ofSeconds(3)));
        status.set(401);
        assertThrows(BadJwtException.class, () -> result.block(Duration.ofSeconds(3)));
        when(local.decode("invalid")).thenReturn(Mono.error(new BadJwtException("invalid")));
        assertThrows(BadJwtException.class, () -> decoder.decode("invalid").block());
        assertEquals(2, challenges.size());
    }

    @Test
    void rejectsUnsafeConfiguration() {
        for (String value : List.of("file:///tmp/auth", "http://user:secret@localhost/check",
                "http://localhost/check?token=x", "http://localhost/check#fragment", "/relative")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SessionAuthorityClient(URI.create(value), "ACCESS"));
        }
        assertThrows(IllegalArgumentException.class, () -> new SessionAuthorityClient(uri("/check"), "ACCESS; OTHER"));
        assertThrows(IllegalArgumentException.class,
                () -> new SessionAuthorityClient(uri("/check"), "ACCESS", Duration.ofSeconds(11)));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
