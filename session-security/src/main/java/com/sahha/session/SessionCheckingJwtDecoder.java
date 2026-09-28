package com.sahha.session;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class SessionCheckingJwtDecoder implements JwtDecoder {
    private final JwtDecoder delegate;
    private final SessionAuthorityClient authority;

    public SessionCheckingJwtDecoder(JwtDecoder delegate, SessionAuthorityClient authority) {
        this.delegate = delegate;
        this.authority = authority;
    }

    @Override
    public Jwt decode(String token) {
        Jwt jwt = delegate.decode(token);
        authority.requireActive(token);
        return jwt;
    }
}
