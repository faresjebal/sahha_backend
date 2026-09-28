package com.sahha.notification.config;

import com.sahha.session.SessionCheckingWebSocketHandler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.sahha.notification.security.NotificationWebSocketHandshakeInterceptor;
import com.sahha.notification.patient.PatientNotificationAccess;
import com.sahha.notification.patient.PatientNotificationHandshake;
import com.sahha.notification.patient.PatientCheckingWebSocketHandler;

@Configuration
@EnableWebSocketMessageBroker
public class NotificationWebSocketConfiguration
		implements WebSocketMessageBrokerConfigurer {

	public static final String ENDPOINT = "/api/v1/notifications/ws";
	public static final String USER_DESTINATION = "/user/queue/notifications";
	public static final String DELIVERY_DESTINATION = "/queue/notifications";
	private final NotificationSecurityProperties securityProperties;
	private final NotificationWebSocketHandshakeInterceptor handshakeInterceptor;
	private final JwtDecoder decoder;
	private final PatientNotificationHandshake patientHandshake;
	private final PatientNotificationAccess patientAccess;

	public NotificationWebSocketConfiguration(
			NotificationSecurityProperties securityProperties,
			NotificationWebSocketHandshakeInterceptor handshakeInterceptor, JwtDecoder decoder,
			PatientNotificationHandshake patientHandshake, PatientNotificationAccess patientAccess) {
		this.securityProperties = securityProperties;
		this.handshakeInterceptor = handshakeInterceptor;
		this.decoder = decoder;
		this.patientHandshake = patientHandshake;
		this.patientAccess = patientAccess;
	}

	@Override
	public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
		registration.addDecoratorFactory(handler -> new SessionCheckingWebSocketHandler(
				new PatientCheckingWebSocketHandler(handler, patientAccess), decoder));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
		registry.enableSimpleBroker("/queue");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint(PatientNotificationHandshake.ENDPOINT)
				.setAllowedOrigins(securityProperties.allowedOrigins().toArray(String[]::new))
				.setHandshakeHandler(patientHandshake)
				.addInterceptors(patientHandshake);
		registry.addEndpoint(ENDPOINT)
				.setAllowedOrigins(
						securityProperties.allowedOrigins().toArray(String[]::new))
				.addInterceptors(handshakeInterceptor);
	}
}
