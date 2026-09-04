package com.sahha.communication.config;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import tools.jackson.databind.ObjectMapper;

import com.sahha.communication.security.CommunicationAccessTokenCookieResolver;
import com.sahha.communication.security.CommunicationAccessTokenValidator;
import com.sahha.communication.security.CommunicationRoleConverter;
import com.sahha.communication.security.CommunicationJwtAuthenticationConverter;
import com.sahha.communication.security.CommunicationSecurityProblemWriter;
import com.sahha.communication.security.CookieAuthenticatedCsrfFilter;

@Configuration
@EnableConfigurationProperties(CommunicationSecurityProperties.class)
public class CommunicationSecurityConfiguration {
	@Bean
	Clock communicationClock() { return Clock.systemUTC(); }

	@Bean
	JwtDecoder communicationJwtDecoder(CommunicationSecurityProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
				properties.jwkSetUri().toString()).jwsAlgorithm(SignatureAlgorithm.RS256).build();
		OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
		OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.audience())
				? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
				: org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
						new OAuth2Error("invalid_token", "The access token is invalid.", null));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
				issuer, audience, new CommunicationAccessTokenValidator())));
		return decoder;
	}

	@Bean
	CookieCsrfTokenRepository communicationCsrfTokenRepository(
			CommunicationSecurityProperties properties) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName(properties.csrfTokenName());
		repository.setHeaderName(properties.csrfHeaderName());
		repository.setCookiePath("/");
		repository.setCookieCustomizer(cookie -> cookie.secure(properties.secureCookies()).sameSite("Lax"));
		return repository;
	}

	@Bean
	Converter<Jwt, AbstractAuthenticationToken> communicationJwtAuthenticationConverter() {
		return new CommunicationJwtAuthenticationConverter();
	}

	@Bean
	SecurityFilterChain communicationSecurityFilterChain(HttpSecurity http,
			JwtDecoder decoder,
			Converter<Jwt, AbstractAuthenticationToken> converter,
			CookieCsrfTokenRepository csrfRepository,
			CommunicationSecurityProperties properties,
			ObjectMapper objectMapper) throws Exception {
		CommunicationSecurityProblemWriter writer = new CommunicationSecurityProblemWriter(objectMapper);
		http.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health", "/actuator/health/**",
						"/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
				.requestMatchers("/api/v1/conversations", "/api/v1/conversations/**").hasRole("DOCTOR")
				.anyRequest().denyAll())
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfRepository))
			.addFilterBefore(new CookieAuthenticatedCsrfFilter(
					properties.accessTokenCookieName(), properties.csrfTokenName(),
					properties.csrfHeaderName(), writer), CsrfFilter.class)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.oauth2ResourceServer(resource -> resource
					.bearerTokenResolver(new CommunicationAccessTokenCookieResolver(properties))
					.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
					.authenticationEntryPoint((request, response, exception) -> writer.unauthorized(request, response)))
			.exceptionHandling(exceptions -> exceptions
					.authenticationEntryPoint((request, response, exception) -> writer.unauthorized(request, response))
					.accessDeniedHandler((request, response, exception) -> writer.forbidden(request, response)))
			.formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable()).requestCache(cache -> cache.disable());
		return http.build();
	}
}
