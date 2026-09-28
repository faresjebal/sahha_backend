package com.sahha.communication.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;
import com.sahha.communication.security.CommunicationWebSocketHandshakeInterceptor;

class CommunicationWebSocketPropertiesTests {
    private CommunicationWebSocketProperties bind(Map<String,Object> values) throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test", values));
        environment.getPropertySources().addLast(new PropertiesPropertySource("shipped",
                PropertiesLoaderUtils.loadProperties(new FileSystemResource("src/main/resources/application.properties"))));
        return Binder.get(environment).bind("sahha.communication.websocket",
                Bindable.of(CommunicationWebSocketProperties.class)).get();
    }
    @Test void defaultRetainsTheExistingExactLocalOrigin() throws Exception {
        assertEquals(List.of("http://localhost:5173"), bind(Map.of()).allowedOrigins());
    }
    @Test void shippedPropertiesUseTheConfiguredFrontendOriginsWithoutImplicitLocalhost() throws Exception {
        var value = bind(Map.of("FRONTEND_ALLOWED_ORIGINS", "http://127.0.0.1:5173,https://sahha.example.test"));
        assertEquals(List.of("http://127.0.0.1:5173", "https://sahha.example.test"), value.allowedOrigins());
        assertThrows(UnsupportedOperationException.class, () -> value.allowedOrigins().add("http://localhost:5173"));
    }
    @Test void rejectsWildcardsCredentialsPathsAndNonHttpOrigins() {
        assertThrows(IllegalArgumentException.class, () -> new CommunicationWebSocketProperties(null));
        assertThrows(IllegalArgumentException.class, () -> new CommunicationWebSocketProperties(List.of()));
        for (String origin : List.of("", "*", "null", "https://*.example.test", "file:///tmp", "ws://localhost:5173",
                "https://user:pass@example.test", "https://example.test/", "https://example.test?x=1", "https://example.test#x")) {
            assertThrows(IllegalArgumentException.class, () -> new CommunicationWebSocketProperties(List.of(origin)));
        }
        assertEquals(List.of("https://example.test"), new CommunicationWebSocketProperties(
                List.of(" https://example.test ", "https://example.test")).allowedOrigins());
    }
    @Test void endpointRegistersOnlyTheValidatedAllowlistAndPreservesAuthentication() {
        var registry = mock(StompEndpointRegistry.class);
        var registration = mock(StompWebSocketEndpointRegistration.class, RETURNS_SELF);
        var authentication = mock(CommunicationWebSocketHandshakeInterceptor.class);
        when(registry.addEndpoint(CommunicationWebSocketConfiguration.ENDPOINT)).thenReturn(registration);
        new CommunicationWebSocketConfiguration(authentication, mock(JwtDecoder.class),
                new CommunicationWebSocketProperties(List.of("http://127.0.0.1:5173"))).registerStompEndpoints(registry);
        verify(registry).setOrder(-1);
        verify(registration).setAllowedOrigins("http://127.0.0.1:5173");
        verify(registration).addInterceptors(authentication);
        verify(registration, never()).setAllowedOriginPatterns(any());
    }
    @Test void configuredOriginIsAcceptedAndOtherBrowserOriginsStayDenied() throws Exception {
        var interceptor = new OriginHandshakeInterceptor(List.of("http://127.0.0.1:5173"));
        for (String origin : List.of("http://127.0.0.1:5173", "http://localhost:5173", "https://untrusted.example.test")) {
            var request = new MockHttpServletRequest("GET", CommunicationWebSocketConfiguration.ENDPOINT);
            request.setServerName("127.0.0.1"); request.setServerPort(8086); request.addHeader("Origin", origin);
            var response = new MockHttpServletResponse();
            boolean permitted = interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                    new ServletServerHttpResponse(response), mock(WebSocketHandler.class), Map.of());
            assertEquals(origin.equals("http://127.0.0.1:5173"), permitted);
            if (!permitted) assertEquals(403, response.getStatus());
        }
    }
}
