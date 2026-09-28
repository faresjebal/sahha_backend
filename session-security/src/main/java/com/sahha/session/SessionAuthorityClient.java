package com.sahha.session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.springframework.security.oauth2.jwt.BadJwtException;

/** An uncached decision by Auth, not an alternative to local JWT validation. */
public final class SessionAuthorityClient implements AutoCloseable {
    public static final String CHALLENGE_HEADER = "X-Sahha-Session-Check";
    private final URI uri;
    private final String cookieName;
    private final Duration timeout;
    private final HttpClient client;

    public SessionAuthorityClient(URI uri, String cookieName) {
        this(uri, cookieName, Duration.ofSeconds(3));
    }

    public SessionAuthorityClient(URI uri, String cookieName, Duration timeout) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("Session authority must be a trusted absolute HTTP(S) URI");
        }
        if (cookieName == null || !cookieName.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Invalid access cookie name");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("Session check timeout must be positive and at most 10 seconds");
        }
        this.uri = uri;
        this.cookieName = cookieName;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
    }

    public void requireActive(String token) {
        try {
            requireActiveAsync(token).get();
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw denied();
        }
        catch (java.util.concurrent.ExecutionException failure) {
            throw denied();
        }
    }

    public CompletableFuture<Void> requireActiveAsync(String token) {
        // Only a compact, locally validated JWT is forwarded. Never forward the browser's other cookies.
        if (token == null || token.length() > 8192
                || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            return CompletableFuture.failedFuture(denied());
        }
        String challenge = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Cookie", cookieName + "=" + token)
                .header(CHALLENGE_HEADER, challenge)
                .header("Cache-Control", "no-store").GET().build();
        try {
            // Complete at headers and close the unused stream: a broken error response
            // must not hold a servlet thread by streaming a body indefinitely.
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .handle((response, failure) -> {
                        if (failure != null) throw new CompletionException(denied());
                        try (var body = response.body()) {
                            if (response.statusCode() != 204
                                    || !response.headers().allValues(CHALLENGE_HEADER).equals(List.of(challenge))) {
                                throw denied();
                            }
                            return null;
                        }
                        catch (Exception invalidResponse) {
                            // Do not retain causes that might contain credentials or transport details.
                            throw new CompletionException(denied());
                        }
                    });
        }
        catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(denied());
        }
    }

    private static BadJwtException denied() {
        return new BadJwtException("The access session could not be verified.");
    }

    @Override
    public void close() {
        client.shutdownNow();
    }
}
