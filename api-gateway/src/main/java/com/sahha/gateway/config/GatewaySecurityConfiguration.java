package com.sahha.gateway.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import com.sahha.gateway.security.GatewayAccessTokenCookieResolver;
import com.sahha.gateway.security.GatewayAccessTokenValidator;
import com.sahha.gateway.security.GatewayPlatformRoleConverter;
import com.sahha.gateway.security.GatewaySecurityProblemWriter;

@Configuration
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfiguration {

	@Bean
	ReactiveJwtDecoder gatewayJwtDecoder(GatewaySecurityProperties properties) {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
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
				List.of(issuer, audience, new GatewayAccessTokenValidator())));
		return decoder;
	}

	@Bean
	GatewayAccessTokenCookieResolver gatewayBearerTokenConverter(
			GatewaySecurityProperties properties) {
		return new GatewayAccessTokenCookieResolver(properties);
	}

	@Bean
	Converter<Jwt, Mono<AbstractAuthenticationToken>>
			gatewayJwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(
				new GatewayPlatformRoleConverter());
		return new ReactiveJwtAuthenticationConverterAdapter(converter);
	}

	@Bean
	CorsConfigurationSource gatewayCorsConfigurationSource(
			GatewaySecurityProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(List.of(
				HttpMethod.GET.name(),
				HttpMethod.POST.name(),
				HttpMethod.PUT.name(),
				HttpMethod.PATCH.name(),
				HttpMethod.DELETE.name(),
				HttpMethod.OPTIONS.name()));
		configuration.setAllowedHeaders(List.of(
				HttpHeaders.CONTENT_TYPE,
				HttpHeaders.ACCEPT,
				"X-XSRF-TOKEN",
				"X-Upload-Token",
				"X-Download-Token",
				"X-Request-ID"));
		configuration.setExposedHeaders(List.of(
				"X-Request-ID",
				HttpHeaders.LOCATION));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source =
				new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	SecurityWebFilterChain gatewaySecurityWebFilterChain(
			ServerHttpSecurity http,
			ReactiveJwtDecoder jwtDecoder,
			GatewayAccessTokenCookieResolver bearerTokenConverter,
			Converter<Jwt, Mono<AbstractAuthenticationToken>>
					jwtAuthenticationConverter,
			CorsConfigurationSource gatewayCorsConfigurationSource,
			ObjectMapper objectMapper) {
		GatewaySecurityProblemWriter problemWriter =
				new GatewaySecurityProblemWriter(objectMapper);
		http
				.authorizeExchange(authorize -> authorize
						.pathMatchers(
								"/actuator/health",
								"/actuator/health/**")
						.permitAll()
						.pathMatchers(
								HttpMethod.GET,
								"/.well-known/jwks.json",
								"/api/v1/auth/csrf")
						.permitAll()
						.pathMatchers(
								HttpMethod.POST,
								"/api/v1/auth/registrations",
								"/api/v1/auth/email-verifications/confirm",
								"/api/v1/auth/email-verifications/resend",
								"/api/v1/auth/password-resets/request",
								"/api/v1/auth/password-resets/confirm",
								"/api/v1/auth/login",
								"/api/v1/auth/refresh")
						.permitAll()
						.pathMatchers(HttpMethod.OPTIONS, "/**")
						.permitAll()
						.pathMatchers("/api/v1/platform/**")
						.hasRole("PLATFORM_ADMIN")
						.pathMatchers("/api/v1/**")
						.authenticated()
						.anyExchange()
						.denyAll())
				.cors(cors -> cors.configurationSource(
						gatewayCorsConfigurationSource))
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenConverter(bearerTokenConverter)
						.jwt(jwt -> jwt
								.jwtDecoder(jwtDecoder)
								.jwtAuthenticationConverter(
										jwtAuthenticationConverter))
						.authenticationEntryPoint(
								(exchange, exception) ->
										problemWriter.unauthorized(exchange)))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(
								(exchange, exception) ->
										problemWriter.unauthorized(exchange))
						.accessDeniedHandler(
								(exchange, exception) ->
										problemWriter.forbidden(exchange)))
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
						.referrerPolicy(referrer -> referrer.policy(
								ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy
										.NO_REFERRER))
						.permissionsPolicy(permissions -> permissions.policy(
								"camera=(), microphone=(), geolocation=()")))
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.logout(ServerHttpSecurity.LogoutSpec::disable)
				.requestCache(ServerHttpSecurity.RequestCacheSpec::disable);
		return http.build();
	}
}
