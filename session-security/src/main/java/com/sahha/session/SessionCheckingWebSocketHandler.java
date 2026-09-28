package com.sahha.session;

import java.io.IOException;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

/** Revalidates before incoming frames and before broker delivery, including on an already open connection. */
public final class SessionCheckingWebSocketHandler extends WebSocketHandlerDecorator {
    private final JwtDecoder decoder;

    public SessionCheckingWebSocketHandler(WebSocketHandler delegate, JwtDecoder decoder) {
        super(delegate);
        this.decoder = decoder;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!active(session)) return;
        super.afterConnectionEstablished(new WebSocketSessionDecorator(session) {
            @Override
            public void sendMessage(WebSocketMessage<?> message) throws IOException {
                if (!active(this)) throw new IOException("The access session could not be verified.");
                super.sendMessage(message);
            }
        });
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (active(session)) super.handleMessage(session, message);
    }

    private boolean active(WebSocketSession session) throws IOException {
        if (session.getPrincipal() instanceof JwtAuthenticationToken authentication
                && authentication.isAuthenticated()) {
            try {
                decoder.decode(authentication.getToken().getTokenValue());
                return true;
            }
            catch (JwtException invalid) {
                // No protected payload reaches the transport after a failed decision.
            }
        }
        session.close(CloseStatus.POLICY_VIOLATION);
        return false;
    }
}
