package com.sahha.scheduling.config;

import org.springframework.http.HttpMethod;
import com.sahha.scheduling.security.SchedulingPermissions;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import com.sahha.session.SessionAuthorityClient;
import com.sahha.session.SessionCheckingJwtDecoder;

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

import com.sahha.scheduling.security.CookieAuthenticatedCsrfFilter;
import com.sahha.scheduling.security.SchedulingAccessTokenCookieResolver;
import com.sahha.scheduling.security.SchedulingAccessTokenValidator;
import com.sahha.scheduling.security.SchedulingRoleConverter;
import com.sahha.scheduling.security.SchedulingSecurityProblemWriter;

@Configuration
@EnableConfigurationProperties(SchedulingSecurityProperties.class)
public class SchedulingSecurityConfiguration {

	@Bean
	Clock schedulingClock() {
		return Clock.systemUTC();
	}

	@Bean
	JwtDecoder schedulingJwtDecoder(
			SchedulingSecurityProperties properties, SessionAuthorityClient sessionAuthority) {
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
				List.of(issuer, audience, new SchedulingAccessTokenValidator())));
		return new SessionCheckingJwtDecoder(decoder, sessionAuthority);
	}

	@Bean(destroyMethod = "close")
	SessionAuthorityClient schedulingSessionAuthorityClient(
			SchedulingSecurityProperties properties,
			@Value("${AUTH_SESSION_CHECK_URI:http://localhost:8081/api/v1/internal/auth/session-check}") URI uri) {
		return new SessionAuthorityClient(uri, properties.accessTokenCookieName());
	}

	@Bean
	CookieCsrfTokenRepository schedulingCsrfTokenRepository(
			SchedulingSecurityProperties properties) {
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
			schedulingJwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new SchedulingRoleConverter());
		return converter;
	}

	@Bean
	SecurityFilterChain schedulingSecurityFilterChain(
			HttpSecurity http,
			JwtDecoder jwtDecoder,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
			CookieCsrfTokenRepository csrfTokenRepository,
			SchedulingSecurityProperties properties,
			ObjectMapper objectMapper) throws Exception {
		SchedulingSecurityProblemWriter problemWriter =
				new SchedulingSecurityProblemWriter(objectMapper);
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health",
								"/actuator/health/**",
								"/v3/api-docs/**",
								"/swagger-ui.html",
								"/swagger-ui/**")
						.permitAll()
						.requestMatchers("/api/v1/internal/clinical/appointments/**", "/api/v1/internal/clinical/patients/**")
                        .hasAuthority(SchedulingPermissions.CLINICAL_CONTEXT)
                        .requestMatchers("/api/v1/availability/mine/**", "/api/v1/appointments/mine", "/api/v1/appointments/mine/**")
                        .hasAuthority(SchedulingPermissions.PATIENT_SELF)
                        .requestMatchers("/api/v1/availability/me", "/api/v1/availability/me/**")
                        .hasAuthority(SchedulingPermissions.AVAILABILITY_SELF)
                        .requestMatchers(HttpMethod.GET, "/api/v1/availability/doctors", "/api/v1/availability/doctors/**")
                        .hasAuthority(SchedulingPermissions.DIRECTORY_READ)
                        .requestMatchers(HttpMethod.GET, "/api/v1/appointments", "/api/v1/appointments/*")
                        .hasAuthority(SchedulingPermissions.APPOINTMENT_READ)
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments")
                        .hasAuthority(SchedulingPermissions.BOOK)
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments/*/confirm", "/api/v1/appointments/*/reject")
                        .hasAuthority(SchedulingPermissions.RESPOND)
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments/*/reschedule", "/api/v1/appointments/*/cancel", "/api/v1/appointments/*/no-show")
                        .hasAuthority(SchedulingPermissions.ADJUST)
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments/*/check-in")
                        .hasAuthority(SchedulingPermissions.CHECK_IN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments/*/start", "/api/v1/appointments/*/complete")
                        .hasAuthority(SchedulingPermissions.TREAT)
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
								new SchedulingAccessTokenCookieResolver(properties))
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
