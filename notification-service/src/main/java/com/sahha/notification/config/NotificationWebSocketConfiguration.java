package com.sahha.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.sahha.notification.security.NotificationWebSocketHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class NotificationWebSocketConfiguration
		implements WebSocketMessageBrokerConfigurer {

	public static final String ENDPOINT = "/api/v1/notifications/ws";
	public static final String USER_DESTINATION = "/user/queue/notifications";
	public static final String DELIVERY_DESTINATION = "/queue/notifications";
	private final NotificationSecurityProperties securityProperties;
	private final NotificationWebSocketHandshakeInterceptor handshakeInterceptor;

	public NotificationWebSocketConfiguration(
			NotificationSecurityProperties securityProperties,
			NotificationWebSocketHandshakeInterceptor handshakeInterceptor) {
		this.securityProperties = securityProperties;
		this.handshakeInterceptor = handshakeInterceptor;
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
				.setAllowedOrigins(
						securityProperties.allowedOrigins().toArray(String[]::new))
				.addInterceptors(handshakeInterceptor);
	}
}
