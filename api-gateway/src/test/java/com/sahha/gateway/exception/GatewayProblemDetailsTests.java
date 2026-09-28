package com.sahha.gateway.exception;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.gateway.filter.GatewayRequestContextFilter;
import static org.junit.jupiter.api.Assertions.*;

class GatewayProblemDetailsTests {
    private static final String PRIVATE = "synthetic-private-value";

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, PRIVATE), 503),
                Arguments.of(new ConnectException(PRIVATE), 503),
                Arguments.of(new SocketTimeoutException(PRIVATE), 504),
                Arguments.of(new java.util.concurrent.TimeoutException(PRIVATE), 504),
                Arguments.of(io.netty.handler.timeout.ReadTimeoutException.INSTANCE, 504),
                Arguments.of(new ResponseStatusException(HttpStatus.NOT_FOUND, PRIVATE), 404),
                Arguments.of(new IllegalStateException(PRIVATE), 500));
    }

    @ParameterizedTest
    @MethodSource("errors")
    void safeCorrelatedErrorsKeepStatusWithoutEchoingExceptionOrRequestData(Throwable error, int status) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/example?secret=" + PRIVATE)
                .header("Cookie", "SAHHA_ACCESS_TOKEN=" + PRIVATE));
        exchange.getAttributes().put(GatewayRequestContextFilter.REQUEST_ID_ATTRIBUTE, "synthetic-request-123");
        var mapper = JsonMapper.builder().build();
        new GatewayProblemDetailsHandler(mapper).handle(exchange, error).block();
        assertEquals(status, exchange.getResponse().getStatusCode().value());
        assertEquals("application/problem+json", exchange.getResponse().getHeaders().getContentType().toString());
        assertEquals("no-store", exchange.getResponse().getHeaders().getCacheControl());
        assertNull(exchange.getResponse().getHeaders().getFirst("Set-Cookie"));
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        assertFalse(body.contains(PRIVATE));
        var problem = mapper.readTree(body);
        assertEquals("/api/v1/example", problem.get("instance").asString());
        assertEquals("synthetic-request-123", problem.get("requestId").asString());
        assertEquals(status, problem.get("status").asInt());
    }

    @Test
    void committedResponsesAreNotRewritten() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/example"));
        exchange.getResponse().setComplete().block();
        var failure = new IllegalStateException(PRIVATE);
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                new GatewayProblemDetailsHandler(JsonMapper.builder().build()).handle(exchange, failure).block()));
    }
}
