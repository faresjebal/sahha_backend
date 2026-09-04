package com.sahha.notification.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import tools.jackson.databind.ObjectMapper;

import com.sahha.notification.security.CookieAuthenticatedCsrfFilter;
import com.sahha.notification.security.NotificationAccessTokenCookieResolver;
import com.sahha.notification.security.NotificationAccessTokenValidator;
import com.sahha.notification.security.NotificationJwtAuthenticationConverter;
import com.sahha.notification.security.NotificationSecurityProblemWriter;

@Configuration
@EnableConfigurationProperties(NotificationSecurityProperties.class)
public class NotificationSecurityConfiguration {

	@Bean
	JwtDecoder notificationJwtDecoder(NotificationSecurityProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder
				.withJwkSetUri(properties.jwkSetUri().toString())
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.build();
		OAuth2TokenValidator<Jwt> issuer =
				JwtValidators.createDefaultWithIssuer(properties.issuer());
		OAuth2TokenValidator<Jwt> audience = token ->
				token.getAudience().contains(properties.audience())
						? org.springframework.security.oauth2.core
								.OAuth2TokenValidatorResult.success()
						: org.springframework.security.oauth2.core
								.OAuth2TokenValidatorResult.failure(
										new org.springframework.security.oauth2.core
												.OAuth2Error(
														"invalid_token",
														"The access token is invalid.",
														null));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				List.of(issuer, audience, new NotificationAccessTokenValidator())));
		return decoder;
	}

	@Bean
	CookieCsrfTokenRepository notificationCsrfTokenRepository(
			NotificationSecurityProperties properties) {
		CookieCsrfTokenRepository repository =
				CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName(properties.csrfTokenName());
		repository.setHeaderName(properties.csrfHeaderName());
		repository.setCookiePath("/");
		repository.setCookieCustomizer(cookie -> cookie
				.secure(properties.secureCookies())
				.sameSite("Lax"));
		return repository;
	}

	@Bean
	Converter<Jwt, AbstractAuthenticationToken>
			notificationJwtAuthenticationConverter() {
		return new NotificationJwtAuthenticationConverter();
	}

	@Bean
	SecurityFilterChain notificationSecurityFilterChain(
			HttpSecurity http,
			JwtDecoder jwtDecoder,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
			CookieCsrfTokenRepository csrfTokenRepository,
			NotificationSecurityProperties properties,
			ObjectMapper objectMapper) throws Exception {
		NotificationSecurityProblemWriter problemWriter =
				new NotificationSecurityProblemWriter(objectMapper);
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health",
								"/actuator/health/**",
								"/v3/api-docs/**",
								"/swagger-ui.html",
								"/swagger-ui/**")
						.permitAll()
						.requestMatchers(
								"/api/v1/notifications",
								"/api/v1/notifications/**")
						.authenticated()
						.anyRequest()
						.denyAll())
				.csrf(csrf -> csrf
						.spa()
						.csrfTokenRepository(csrfTokenRepository))
				.addFilterBefore(
						new CookieAuthenticatedCsrfFilter(
								properties.accessTokenCookieName(),
								properties.csrfTokenName(),
								properties.csrfHeaderName(),
								problemWriter),
						CsrfFilter.class)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenResolver(
								new NotificationAccessTokenCookieResolver(properties))
						.jwt(jwt -> jwt
								.decoder(jwtDecoder)
								.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint(
								(request, response, exception) ->
										problemWriter.unauthorized(request, response)))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(
								(request, response, exception) ->
										problemWriter.unauthorized(request, response))
						.accessDeniedHandler(
								(request, response, exception) ->
										problemWriter.forbidden(request, response)))
				.formLogin(form -> form.disable())
				.httpBasic(httpBasic -> httpBasic.disable())
				.logout(logout -> logout.disable())
				.requestCache(requestCache -> requestCache.disable());
		return http.build();
	}
}
