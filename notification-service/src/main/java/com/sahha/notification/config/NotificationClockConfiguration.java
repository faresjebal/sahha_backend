package com.sahha.notification.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationClockConfiguration {

	@Bean
	Clock notificationClock() {
		return Clock.systemUTC();
	}
}
