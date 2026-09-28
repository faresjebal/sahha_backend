package com.sahha.session;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

public final class SessionCheckingReactiveJwtDecoder implements ReactiveJwtDecoder {
    private final ReactiveJwtDecoder delegate;
    private final SessionAuthorityClient authority;

    public SessionCheckingReactiveJwtDecoder(ReactiveJwtDecoder delegate, SessionAuthorityClient authority) {
        this.delegate = delegate;
        this.authority = authority;
    }

    @Override
    public Mono<Jwt> decode(String token) {
        return delegate.decode(token).flatMap(jwt ->
                Mono.fromFuture(() -> authority.requireActiveAsync(token)).thenReturn(jwt));
    }
}
