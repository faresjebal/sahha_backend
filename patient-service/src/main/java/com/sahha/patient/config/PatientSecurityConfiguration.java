package com.sahha.patient.config;

import org.springframework.http.HttpMethod;
import com.sahha.patient.security.PatientPermissions;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import com.sahha.session.SessionAuthorityClient;
import com.sahha.session.SessionCheckingJwtDecoder;

import java.time.Clock;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import com.sahha.patient.security.CookieAuthenticatedCsrfFilter;
import com.sahha.patient.security.PatientAccessTokenCookieResolver;
import com.sahha.patient.security.PatientAccessTokenValidator;
import com.sahha.patient.security.PatientRoleConverter;
import com.sahha.patient.security.PatientSecurityProblemWriter;

@Configuration
@EnableConfigurationProperties(PatientSecurityProperties.class)
public class PatientSecurityConfiguration {

	@Bean
	Clock patientClock() {
		return Clock.systemUTC();
	}

	@Bean
	JwtDecoder patientJwtDecoder(
			PatientSecurityProperties properties, SessionAuthorityClient sessionAuthority) {
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
				List.of(issuer, audience, new PatientAccessTokenValidator())));
		return new SessionCheckingJwtDecoder(decoder, sessionAuthority);
	}

	@Bean(destroyMethod = "close")
	SessionAuthorityClient patientSessionAuthorityClient(
			PatientSecurityProperties properties,
			@Value("${AUTH_SESSION_CHECK_URI:http://localhost:8081/api/v1/internal/auth/session-check}") URI uri) {
		return new SessionAuthorityClient(uri, properties.accessTokenCookieName());
	}

	@Bean
	CookieCsrfTokenRepository patientCsrfTokenRepository(
			PatientSecurityProperties properties) {
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
			patientJwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new PatientRoleConverter());
		return converter;
	}

	@Bean
	SecurityFilterChain patientSecurityFilterChain(
			HttpSecurity http,
			JwtDecoder jwtDecoder,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
			CookieCsrfTokenRepository csrfTokenRepository,
			PatientSecurityProperties properties,
			ObjectMapper objectMapper)
			throws Exception {
		PatientSecurityProblemWriter problemWriter =
				new PatientSecurityProblemWriter(objectMapper);
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health",
								"/actuator/health/**",
								"/v3/api-docs/**",
								"/swagger-ui.html",
								"/swagger-ui/**")
						.permitAll()
						.requestMatchers("/api/v1/patients/me/**")
                        .hasAuthority(PatientPermissions.SELF_LINK)
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients", "/api/v1/patients/**")
                        .hasAuthority(PatientPermissions.ADMIN_READ)
                        .requestMatchers("/api/v1/patients", "/api/v1/patients/**")
                        .hasAuthority(PatientPermissions.ADMIN_WRITE)
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
								new PatientAccessTokenCookieResolver(properties))
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
