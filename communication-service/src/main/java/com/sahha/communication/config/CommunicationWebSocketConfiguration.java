package com.sahha.communication.config;

import com.sahha.session.SessionCheckingWebSocketHandler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.sahha.communication.security.CommunicationWebSocketHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(CommunicationWebSocketProperties.class)
public class CommunicationWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
	public static final String ENDPOINT = "/api/v1/conversations/ws";
	public static final String USER_DESTINATION = "/user/queue/messages";
	private final CommunicationWebSocketHandshakeInterceptor interceptor;
	private final JwtDecoder decoder;
	private final CommunicationWebSocketProperties properties;
	public CommunicationWebSocketConfiguration(
			CommunicationWebSocketHandshakeInterceptor interceptor, JwtDecoder decoder,
			CommunicationWebSocketProperties properties) {
		this.interceptor = interceptor;
		this.decoder = decoder;
		this.properties = properties;
	}
	@Override
	public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
		registration.addDecoratorFactory(handler -> new SessionCheckingWebSocketHandler(handler, decoder));
	}
	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
		registry.enableSimpleBroker("/queue");
	}
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// The reserved /ws path must win over MVC's /{conversationId} route.
		// This changes handler selection only; HTTP and STOMP security still apply.
		registry.setOrder(-1);
		registry.addEndpoint(ENDPOINT)
				.setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new))
				.addInterceptors(interceptor);
	}
}
