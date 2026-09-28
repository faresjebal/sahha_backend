package com.sahha.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Fail closed until Phase 7 introduces separately authorised Audit APIs. */
@Configuration
public class AuditSecurityConfiguration {

	@Bean
	SecurityFilterChain auditSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/actuator/health",
								"/actuator/health/liveness", "/actuator/health/readiness",
								"/actuator/info").permitAll()
						.anyRequest().denyAll())
				// No cookie authentication and every mutation is denied. Avoid the
				// default CSRF repository creating sessions for rejected requests.
				// Phase 7 must add CSRF alongside any authorised cookie-based API.
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, failure) -> response.setStatus(403))
						.accessDeniedHandler((request, response, failure) -> response.setStatus(403)))
				.build();
	}
}
