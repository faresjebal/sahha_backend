package com.sahha.file.config;

import org.springframework.http.HttpMethod;
import com.sahha.file.security.FilePermissions;

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

import com.sahha.file.security.CookieAuthenticatedCsrfFilter;
import com.sahha.file.security.FileAccessTokenCookieResolver;
import com.sahha.file.security.FileAccessTokenValidator;
import com.sahha.file.security.FileRoleConverter;
import com.sahha.file.security.FileSecurityProblemWriter;

@Configuration
@EnableConfigurationProperties(FileSecurityProperties.class)
public class FileSecurityConfiguration {

	@Bean
	Clock fileClock() {
		return Clock.systemUTC();
	}

	@Bean
	JwtDecoder fileJwtDecoder(
			FileSecurityProperties properties, SessionAuthorityClient sessionAuthority) {
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
				List.of(issuer, audience, new FileAccessTokenValidator())));
		return new SessionCheckingJwtDecoder(decoder, sessionAuthority);
	}

	@Bean(destroyMethod = "close")
	SessionAuthorityClient fileSessionAuthorityClient(
			FileSecurityProperties properties,
			@Value("${AUTH_SESSION_CHECK_URI:http://localhost:8081/api/v1/internal/auth/session-check}") URI uri) {
		return new SessionAuthorityClient(uri, properties.accessTokenCookieName());
	}

	@Bean
	CookieCsrfTokenRepository fileCsrfTokenRepository(
			FileSecurityProperties properties) {
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
	Converter<Jwt, AbstractAuthenticationToken> fileJwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new FileRoleConverter());
		return converter;
	}

	@Bean
	SecurityFilterChain fileSecurityFilterChain(
			HttpSecurity http,
			JwtDecoder jwtDecoder,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
			CookieCsrfTokenRepository csrfTokenRepository,
			FileSecurityProperties properties,
			ObjectMapper objectMapper) throws Exception {
		FileSecurityProblemWriter problemWriter =
				new FileSecurityProblemWriter(objectMapper);
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health", "/actuator/health/**",
								"/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
						.permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/message-attachments/*/synthetic-scan")
                        .hasAuthority(FilePermissions.SYNTHETIC_SCAN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/message-attachments/uploads")
                        .hasAuthority(FilePermissions.UPLOAD_MESSAGE)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/files/message-attachments/*/content")
                        .hasAuthority(FilePermissions.UPLOAD_MESSAGE)
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/message-attachments/*/download-grants")
                        .hasAuthority(FilePermissions.READ_MESSAGE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/message-attachments/**")
                        .hasAuthority(FilePermissions.READ_MESSAGE)
                        .requestMatchers("/api/v1/files/shared/**")
                        .hasAuthority(FilePermissions.READ_SHARED)
                        .requestMatchers("/api/v1/files/shared-care/**")
                        .hasAuthority(FilePermissions.READ_SHARED_CARE)
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/*/synthetic-scan")
                        .hasAuthority(FilePermissions.SYNTHETIC_SCAN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/uploads")
                        .hasAuthority(FilePermissions.UPLOAD_OWN)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/files/*/content")
                        .hasAuthority(FilePermissions.UPLOAD_OWN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/*/download-grants")
                        .hasAuthority(FilePermissions.READ_OWN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/files", "/api/v1/files/**")
                        .hasAuthority(FilePermissions.READ_OWN)
                        .anyRequest().denyAll())
				.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
				.addFilterBefore(new CookieAuthenticatedCsrfFilter(
						properties.accessTokenCookieName(), properties.csrfTokenName(),
						properties.csrfHeaderName(), problemWriter), CsrfFilter.class)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenResolver(new FileAccessTokenCookieResolver(properties))
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
