package com.sahha.communication.config;

import com.sahha.communication.security.CommunicationPermissions;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.web.csrf.CsrfChannelInterceptor;

import static org.springframework.messaging.simp.SimpMessageType.MESSAGE;
import static org.springframework.messaging.simp.SimpMessageType.SUBSCRIBE;

@Configuration
@EnableWebSocketSecurity
public class CommunicationWebSocketSecurityConfiguration {
	// Spring selects this override by name. The SPA sends the raw cookie-bound
	// token returned by Auth, matching Notification's existing STOMP contract.
	@Bean("csrfChannelInterceptor")
	ChannelInterceptor communicationCsrfChannelInterceptor() {
		return new CsrfChannelInterceptor();
	}
	@Bean
	AuthorizationManager<Message<?>> communicationMessageAuthorizationManager(
			MessageMatcherDelegatingAuthorizationManager.Builder messages) {
		messages.nullDestMatcher().hasAuthority(CommunicationPermissions.MESSAGE_STREAM)
				.simpSubscribeDestMatchers(CommunicationWebSocketConfiguration.USER_DESTINATION)
				.hasAuthority(CommunicationPermissions.MESSAGE_STREAM)
				.simpTypeMatchers(MESSAGE, SUBSCRIBE).denyAll()
				.anyMessage().denyAll();
		return messages.build();
	}
}
