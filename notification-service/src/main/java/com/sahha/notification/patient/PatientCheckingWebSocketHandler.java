package com.sahha.notification.patient;

import java.io.IOException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

/** Session verification is retained by the outer shared-session decorator.
 * Patient registration ownership is additionally checked before inbound AND outbound frames. */
public final class PatientCheckingWebSocketHandler extends WebSocketHandlerDecorator {
    private final PatientNotificationAccess access;
    public PatientCheckingWebSocketHandler(WebSocketHandler delegate, PatientNotificationAccess access) {
        super(delegate); this.access = access;
    }
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!active(session)) return;
        super.afterConnectionEstablished(new WebSocketSessionDecorator(session) {
            @Override
            public void sendMessage(WebSocketMessage<?> message) throws IOException {
                if (!active(this)) throw new IOException("Patient access could not be verified.");
                super.sendMessage(message);
            }
        });
    }
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (active(session)) super.handleMessage(session, message);
    }
    private boolean active(WebSocketSession session) throws IOException {
        if (session.getUri() == null || !PatientNotificationHandshake.ENDPOINT.equals(session.getUri().getPath())) {
            return true;
        }
        if (session.getAttributes().get(PatientNotificationHandshake.CONTEXT_ATTRIBUTE) instanceof PatientNotificationContext context
                && session.getPrincipal() instanceof JwtAuthenticationToken authentication
                && context.principalName().equals(authentication.getName())) {
            try {
                if (context.equals(access.requireOwnRegistration(context.registrationId(), authentication.getToken()))) return true;
            } catch (RuntimeException denied) {
                // Fail closed on ownership changes, deactivation and dependency failure. No identifiers are logged.
            }
        }
        session.close(CloseStatus.POLICY_VIOLATION);
        return false;
    }
}
