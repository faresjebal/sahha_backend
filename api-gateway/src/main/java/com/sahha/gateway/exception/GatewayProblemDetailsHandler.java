package com.sahha.gateway.exception;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import com.sahha.gateway.filter.GatewayRequestContextFilter;

/** Handles edge-generated failures, never rewrites a domain response or grants access. */
@Component
@Order(-2)
public class GatewayProblemDetailsHandler implements WebExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayProblemDetailsHandler.class);
    private final ObjectMapper mapper;

    public GatewayProblemDetailsHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) return Mono.error(exception);
        int status = status(exception);
        String requestId = exchange.getAttributeOrDefault(
                GatewayRequestContextFilter.REQUEST_ID_ATTRIBUTE, "unavailable");
        // No raw URL, query, headers, response body, exception message or stack trace.
        LOGGER.warn("Gateway request failure requestId={} status={} exception={}",
                requestId, status, exception.getClass().getSimpleName());
        Map<String, Object> problem = new LinkedHashMap<>();
        HttpStatus known = HttpStatus.resolve(status);
        problem.put("type", "urn:sahha:problem:http-" + status);
        problem.put("title", known == null ? "Request failed" : known.getReasonPhrase());
        problem.put("status", status);
        problem.put("detail", switch (status) {
            case 400 -> "The request is invalid.";
            case 404 -> "The requested resource was not found.";
            case 405 -> "The HTTP method is not supported for this resource.";
            case 503 -> "The required service is temporarily unavailable.";
            case 504 -> "The required service did not respond in time.";
            default -> "The request could not be completed.";
        });
        problem.put("instance", exchange.getRequest().getPath().value());
        problem.put("requestId", requestId);
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatusCode.valueOf(status));
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl("no-store");
        headers.set(GatewayRequestContextFilter.REQUEST_ID_HEADER, requestId);
        byte[] body = mapper.writeValueAsBytes(problem);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private static int status(Throwable error) {
        if (error instanceof ErrorResponse response) {
            int status = response.getStatusCode().value();
            return status >= 400 && status <= 599 ? status : 500;
        }
        if (error instanceof ConnectException) return 503;
        if (error instanceof SocketTimeoutException || error instanceof TimeoutException
                || error instanceof io.netty.handler.timeout.ReadTimeoutException) return 504;
        return 500;
    }
}
