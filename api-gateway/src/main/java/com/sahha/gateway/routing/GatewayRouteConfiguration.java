package com.sahha.gateway.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class GatewayRouteConfiguration {

	@Bean
	RouterFunction<ServerResponse> authServiceRoutes() {
		return route("auth-service")
				.route(
						RequestPredicates.path("/api/v1/auth/**")
								.or(RequestPredicates.path(
										"/.well-known/jwks.json")),
						http())
				.filter(lb("auth-service"))
				.build();
	}

	@Bean
	RouterFunction<ServerResponse> organisationServiceRoutes() {
		return route("organisation-service")
				.route(
						RequestPredicates.path(
								"/api/v1/platform/organisations/**")
								.or(RequestPredicates.path(
										"/api/v1/platform/organisations")),
						http())
				.filter(lb("organisation-service"))
				.build();
	}
}
