package com.sahha.clinical.config;

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

import com.sahha.clinical.security.ClinicalAccessTokenCookieResolver;
import com.sahha.clinical.security.ClinicalAccessTokenValidator;
import com.sahha.clinical.security.ClinicalRoleConverter;
import com.sahha.clinical.security.ClinicalSecurityProblemWriter;
import com.sahha.clinical.security.CookieAuthenticatedCsrfFilter;

@Configuration
@EnableConfigurationProperties(ClinicalSecurityProperties.class)
public class ClinicalSecurityConfiguration {

	@Bean
	Clock clinicalClock() {
		return Clock.systemUTC();
	}

	@Bean
	JwtDecoder clinicalJwtDecoder(ClinicalSecurityProperties properties) {
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
								.OAuth2TokenValidatorResult.failure(new OAuth2Error(
										"invalid_token", "The access token is invalid.", null));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				List.of(issuer, audience, new ClinicalAccessTokenValidator())));
		return decoder;
	}

	@Bean
	CookieCsrfTokenRepository clinicalCsrfTokenRepository(
			ClinicalSecurityProperties properties) {
		CookieCsrfTokenRepository repository =
				CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName(properties.csrfTokenName());
		repository.setHeaderName(properties.csrfHeaderName());
		repository.setCookiePath("/");
		repository.setCookieCustomizer(cookie -> cookie
				.secure(properties.secureCookies()).sameSite("Lax"));
		return repository;
	}

	@Bean
	Converter<Jwt, AbstractAuthenticationToken> clinicalJwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new ClinicalRoleConverter());
		return converter;
	}

	@Bean
	SecurityFilterChain clinicalSecurityFilterChain(
			HttpSecurity http,
			JwtDecoder jwtDecoder,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
			CookieCsrfTokenRepository csrfTokenRepository,
			ClinicalSecurityProperties properties,
			ObjectMapper objectMapper) throws Exception {
		ClinicalSecurityProblemWriter problemWriter =
				new ClinicalSecurityProblemWriter(objectMapper);
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health", "/actuator/health/**",
								"/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
						.permitAll()
						.requestMatchers("/api/v1/internal/clinical/**")
						.hasRole("DOCTOR")
						.requestMatchers("/api/v1/consultations", "/api/v1/consultations/**")
						.hasRole("DOCTOR")
						.requestMatchers("/api/v1/clinical/**")
						.hasRole("DOCTOR")
						.anyRequest().denyAll())
				.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
				.addFilterBefore(new CookieAuthenticatedCsrfFilter(
						properties.accessTokenCookieName(), properties.csrfTokenName(),
						properties.csrfHeaderName(), problemWriter), CsrfFilter.class)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenResolver(new ClinicalAccessTokenCookieResolver(properties))
						.jwt(jwt -> jwt.decoder(jwtDecoder)
								.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint((request, response, exception) ->
								problemWriter.unauthorized(request, response)))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) ->
								problemWriter.unauthorized(request, response))
						.accessDeniedHandler((request, response, exception) ->
								problemWriter.forbidden(request, response)))
				.formLogin(form -> form.disable())
				.httpBasic(httpBasic -> httpBasic.disable())
				.logout(logout -> logout.disable())
				.requestCache(requestCache -> requestCache.disable());
		return http.build();
	}
}
