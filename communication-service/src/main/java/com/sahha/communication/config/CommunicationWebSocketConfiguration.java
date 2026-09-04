package com.sahha.communication.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.sahha.communication.security.CommunicationWebSocketHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class CommunicationWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
	public static final String ENDPOINT = "/api/v1/conversations/ws";
	public static final String USER_DESTINATION = "/user/queue/messages";
	private final CommunicationWebSocketHandshakeInterceptor interceptor;
	public CommunicationWebSocketConfiguration(
			CommunicationWebSocketHandshakeInterceptor interceptor) {
		this.interceptor = interceptor;
	}
	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
		registry.enableSimpleBroker("/queue");
	}
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint(ENDPOINT)
				.setAllowedOrigins("http://localhost:5173")
				.addInterceptors(interceptor);
	}
}
