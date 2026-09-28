package com.sahha.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

class SessionCheckingWebSocketHandlerTests {
    private final WebSocketHandler broker = mock(WebSocketHandler.class);
    private final WebSocketSession transport = mock(WebSocketSession.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final SessionCheckingWebSocketHandler handler = new SessionCheckingWebSocketHandler(broker, decoder);
    private final TextMessage message = new TextMessage("synthetic protected message");

    @BeforeEach
    void authenticate() {
        Jwt jwt = Jwt.withTokenValue("synthetic.payload.signature").header("alg", "RS256")
                .subject("synthetic").build();
        when(transport.getPrincipal()).thenReturn(new JwtAuthenticationToken(jwt, List.of()));
        when(decoder.decode(jwt.getTokenValue())).thenReturn(jwt);
    }

    @Test
    void checksIncomingFramesAfterHandshakeAndClosesRevokedConnection() throws Exception {
        handler.afterConnectionEstablished(transport);
        handler.handleMessage(transport, message);
        verify(broker).handleMessage(transport, message);
        revoke();
        handler.handleMessage(transport, message);
        verify(broker, times(1)).handleMessage(any(), any());
        verify(transport).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void wrapsBrokerSessionAndBlocksProtectedOutgoingDeliveryAfterRevocation() throws Exception {
        handler.afterConnectionEstablished(transport);
        ArgumentCaptor<WebSocketSession> stored = ArgumentCaptor.forClass(WebSocketSession.class);
        verify(broker).afterConnectionEstablished(stored.capture());
        stored.getValue().sendMessage(message);
        verify(transport).sendMessage(message);
        revoke();
        assertThrows(IOException.class, () -> stored.getValue().sendMessage(message));
        verify(transport, times(1)).sendMessage(any());
        verify(transport).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void deniesRevokedHandshakeBeforeBrokerRegistration() throws Exception {
        revoke();
        handler.afterConnectionEstablished(transport);
        verifyNoInteractions(broker);
        verify(transport).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void requiresAnAuthenticatedJwtPrincipal() throws Exception {
        when(transport.getPrincipal()).thenReturn(null);
        handler.handleMessage(transport, message);
        verifyNoInteractions(broker, decoder);
        verify(transport).close(CloseStatus.POLICY_VIOLATION);
    }

    private void revoke() {
        when(decoder.decode(anyString())).thenThrow(new BadJwtException("The access session could not be verified."));
    }
}
