package com.sahha.organisation.config;

import java.time.Clock;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
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

import com.sahha.organisation.security.CookieAuthenticatedCsrfFilter;
import com.sahha.organisation.security.OrganisationAccessTokenCookieResolver;
import com.sahha.organisation.security.OrganisationAccessTokenValidator;
import com.sahha.organisation.security.OrganisationPlatformRoleConverter;
import com.sahha.organisation.security.OrganisationSecurityProblemWriter;

@Configuration
@EnableConfigurationProperties(OrganisationSecurityProperties.class)
public class OrganisationSecurityConfiguration {

	@Bean
	Clock organisationClock() {
		return Clock.systemUTC();
	}

	@Bean
	JwtDecoder organisationJwtDecoder(
			OrganisationSecurityProperties properties) {
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
				List.of(
						issuer,
						audience,
						new OrganisationAccessTokenValidator())));
		return decoder;
	}

	@Bean
	CookieCsrfTokenRepository organisationCsrfTokenRepository(
			OrganisationSecurityProperties properties) {
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
			organisationJwtAuthenticationConverter() {
		JwtAuthenticationConverter converter =
				new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(
				new OrganisationPlatformRoleConverter());
		return converter;
	}

	@Bean
	SecurityFilterChain organisationSecurityFilterChain(
			HttpSecurity http,
			JwtDecoder jwtDecoder,
			Converter<Jwt, AbstractAuthenticationToken>
					jwtAuthenticationConverter,
			CookieCsrfTokenRepository csrfTokenRepository,
			OrganisationSecurityProperties properties,
			ObjectMapper objectMapper)
			throws Exception {
		OrganisationSecurityProblemWriter problemWriter =
				new OrganisationSecurityProblemWriter(objectMapper);
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health",
								"/actuator/health/**",
								"/v3/api-docs/**",
								"/swagger-ui.html",
								"/swagger-ui/**")
						.permitAll()
						.requestMatchers("/api/v1/platform/organisations/**")
						.hasRole("PLATFORM_ADMIN")
						.requestMatchers(
								HttpMethod.GET,
								"/api/v1/organisations/memberships",
								"/api/v1/organisations/*/membership-context",
								"/api/v1/organisations/*/scheduling-doctors/*",
								"/api/v1/organisations/*/patient-doctors/*")
						.authenticated()
						.requestMatchers(
								HttpMethod.GET,
								"/api/v1/organisations/*/collaboration-doctors",
								"/api/v1/organisations/*/collaboration-doctors/*")
						.hasRole("DOCTOR")
						.requestMatchers("/api/v1/departments/**")
						.hasRole("ORGANIZATION_ADMIN")
						.requestMatchers("/api/v1/staff-invitations/**")
						.hasRole("ORGANIZATION_ADMIN")
						.requestMatchers(
								"/api/v1/staff",
								"/api/v1/staff/**")
						.hasRole("ORGANIZATION_ADMIN")
						.requestMatchers(
								"/api/v1/my/doctor-profile",
								"/api/v1/my/doctor-profile/**")
						.hasRole("DOCTOR")
						.requestMatchers("/api/v1/my/staff-invitations/**")
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
								new OrganisationAccessTokenCookieResolver(
										properties))
						.jwt(jwt -> jwt
								.decoder(jwtDecoder)
								.jwtAuthenticationConverter(
										jwtAuthenticationConverter))
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
