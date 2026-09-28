package com.sahha.notification.config;

import com.sahha.notification.security.NotificationPermissions;

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
public class NotificationWebSocketSecurityConfiguration {

	@Bean
	ChannelInterceptor csrfChannelInterceptor() {
		return new CsrfChannelInterceptor();
	}

	@Bean
	AuthorizationManager<Message<?>> notificationMessageAuthorizationManager(
			MessageMatcherDelegatingAuthorizationManager.Builder messages) {
		messages
				.nullDestMatcher().hasAuthority(NotificationPermissions.STREAM_SELF)
				.simpSubscribeDestMatchers(
						NotificationWebSocketConfiguration.USER_DESTINATION)
				.hasAuthority(NotificationPermissions.STREAM_SELF)
				.simpTypeMatchers(MESSAGE, SUBSCRIBE).denyAll()
				.anyMessage().denyAll();
		return messages.build();
	}
}
