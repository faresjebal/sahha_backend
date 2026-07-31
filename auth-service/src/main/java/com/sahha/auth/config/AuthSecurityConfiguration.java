package com.sahha.auth.config;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(AuthSecurityProperties.class)
public class AuthSecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder(AuthSecurityProperties properties) {
		return new BCryptPasswordEncoder(properties.bcryptStrength());
	}

	@Bean
	SecureRandom secureRandom() {
		return new SecureRandom();
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
