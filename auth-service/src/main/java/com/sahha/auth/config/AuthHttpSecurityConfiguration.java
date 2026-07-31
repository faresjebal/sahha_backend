package com.sahha.auth.config;

import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import com.sahha.auth.security.CookieAuthenticatedCsrfFilter;
import com.sahha.auth.security.AccessTokenCookieBearerTokenResolver;
import com.sahha.auth.security.AuthSecurityProblemWriter;
import com.sahha.auth.security.AuthPlatformRoleConverter;

@Configuration
@EnableConfigurationProperties(AuthCookieProperties.class)
public class AuthHttpSecurityConfiguration {

	@Bean
	CookieCsrfTokenRepository authCsrfTokenRepository(
			AuthCookieProperties properties) {
		CookieCsrfTokenRepository repository =
				CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName(properties.csrfTokenName());
		repository.setHeaderName(properties.csrfHeaderName());
		repository.setCookiePath(properties.accessPath());
		repository.setCookieCustomizer(cookie -> cookie
				.secure(properties.secure())
				.sameSite(properties.sameSite()));
		return repository;
	}

	@Bean
	SecurityFilterChain authSecurityFilterChain(
			HttpSecurity http,
			JwtDecoder jwtDecoder,
			AuthCookieProperties cookieProperties,
			CookieCsrfTokenRepository csrfTokenRepository,
			ObjectMapper objectMapper)
			throws Exception {
		AuthSecurityProblemWriter problemWriter =
				new AuthSecurityProblemWriter(objectMapper);
		JwtAuthenticationConverter authenticationConverter =
				new JwtAuthenticationConverter();
		authenticationConverter.setJwtGrantedAuthoritiesConverter(
				new AuthPlatformRoleConverter());
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health",
								"/actuator/health/**",
								"/v3/api-docs/**",
								"/swagger-ui.html",
								"/swagger-ui/**",
								"/.well-known/jwks.json")
						.permitAll()
						.requestMatchers(
								HttpMethod.GET,
								"/api/v1/auth/csrf")
						.permitAll()
						.requestMatchers(
								HttpMethod.POST,
								"/api/v1/auth/registrations",
								"/api/v1/auth/email-verifications/confirm",
								"/api/v1/auth/email-verifications/resend",
								"/api/v1/auth/password-resets/request",
								"/api/v1/auth/password-resets/confirm",
								"/api/v1/auth/login",
								"/api/v1/auth/refresh")
						.permitAll()
						.requestMatchers("/api/v1/auth/platform/**")
						.hasRole("PLATFORM_ADMIN")
						.anyRequest()
						.authenticated())
				.csrf(csrf -> csrf
						.spa()
						.csrfTokenRepository(csrfTokenRepository))
				.addFilterBefore(
						new CookieAuthenticatedCsrfFilter(
								cookieProperties.accessTokenName(),
								cookieProperties.csrfTokenName(),
								cookieProperties.csrfHeaderName(),
								problemWriter),
						CsrfFilter.class)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenResolver(
								new AccessTokenCookieBearerTokenResolver(
										cookieProperties))
						.jwt(jwt -> jwt
								.decoder(jwtDecoder)
								.jwtAuthenticationConverter(
										authenticationConverter))
						.authenticationEntryPoint(
								(request, response, exception) ->
										problemWriter.unauthorized(
												request,
												response)))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(
								(request, response, exception) ->
										problemWriter.unauthorized(
												request,
												response))
						.accessDeniedHandler(
								(request, response, exception) ->
										problemWriter.forbidden(
												request,
												response)))
				.formLogin(form -> form.disable())
				.httpBasic(httpBasic -> httpBasic.disable())
				.logout(logout -> logout.disable())
				.requestCache(requestCache -> requestCache.disable());
		return http.build();
	}
}
