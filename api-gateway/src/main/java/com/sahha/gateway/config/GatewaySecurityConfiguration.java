package com.sahha.gateway.config;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import com.sahha.gateway.security.GatewayAccessTokenCookieResolver;
import com.sahha.gateway.security.GatewayAccessTokenValidator;
import com.sahha.gateway.security.GatewayPlatformRoleConverter;
import com.sahha.gateway.security.GatewaySecurityProblemWriter;

@Configuration
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfiguration {

	@Bean
	JwtDecoder gatewayJwtDecoder(GatewaySecurityProperties properties) {
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
						new GatewayAccessTokenValidator())));
		return decoder;
	}

	@Bean
	BearerTokenResolver gatewayBearerTokenResolver(
			GatewaySecurityProperties properties) {
		return new GatewayAccessTokenCookieResolver(properties);
	}

	@Bean
	Converter<Jwt, AbstractAuthenticationToken>
			gatewayJwtAuthenticationConverter() {
		JwtAuthenticationConverter converter =
				new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(
				new GatewayPlatformRoleConverter());
		return converter;
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
	SecurityFilterChain gatewaySecurityFilterChain(
			HttpSecurity http,
			JwtDecoder jwtDecoder,
			BearerTokenResolver bearerTokenResolver,
			Converter<Jwt, AbstractAuthenticationToken>
					jwtAuthenticationConverter,
			CorsConfigurationSource gatewayCorsConfigurationSource,
			ObjectMapper objectMapper)
			throws Exception {
		GatewaySecurityProblemWriter problemWriter =
				new GatewaySecurityProblemWriter(objectMapper);
		http
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.ERROR)
						.permitAll()
						.requestMatchers(
								"/actuator/health",
								"/actuator/health/**")
						.permitAll()
						.requestMatchers(
								HttpMethod.GET,
								"/.well-known/jwks.json",
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
						.requestMatchers(HttpMethod.OPTIONS, "/**")
						.permitAll()
						.requestMatchers("/api/v1/platform/**")
						.hasRole("PLATFORM_ADMIN")
						.requestMatchers("/api/v1/**")
						.authenticated()
						.anyRequest()
						.denyAll())
				.cors(cors -> cors.configurationSource(
						gatewayCorsConfigurationSource))
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenResolver(bearerTokenResolver)
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
				.headers(headers -> headers
						.frameOptions(frame -> frame.deny())
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
						.referrerPolicy(referrer -> referrer.policy(
								ReferrerPolicyHeaderWriter.ReferrerPolicy
										.NO_REFERRER))
						.permissionsPolicyHeader(permissions -> permissions
								.policy(
										"camera=(), microphone=(), geolocation=()")))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable);
		return http.build();
	}

}
