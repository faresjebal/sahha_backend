package com.sahha.gateway.routing;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfiguration {

	@Bean
	RouteLocator sahhaRoutes(RouteLocatorBuilder builder) {
		return builder.routes()
				.route("auth-service", route -> route
						.path("/api/v1/auth/**", "/.well-known/jwks.json")
						.uri("lb://auth-service"))
				.route("organisation-service", route -> route
						.path(
								"/api/v1/platform/organisations/**",
								"/api/v1/platform/organisations",
								"/api/v1/organisations/**",
								"/api/v1/departments/**",
								"/api/v1/departments",
								"/api/v1/staff-invitations/**",
								"/api/v1/staff-invitations",
								"/api/v1/my/staff-invitations/**",
								"/api/v1/my/staff-invitations",
								"/api/v1/staff/**",
								"/api/v1/staff",
								"/api/v1/my/doctor-profile/**",
								"/api/v1/my/doctor-profile")
						.uri("lb://organisation-service"))
				.route("patient-service", route -> route
						.path("/api/v1/patients/**", "/api/v1/patients")
						.uri("lb://patient-service"))
				.route("scheduling-service", route -> route
						.path(
								"/api/v1/availability/**",
								"/api/v1/availability",
								"/api/v1/appointments/**",
								"/api/v1/appointments")
						.uri("lb://scheduling-service"))
				.route("clinical-service", route -> route
						.path(
								"/api/v1/consultations/**",
								"/api/v1/consultations",
								"/api/v1/clinical/**")
						.uri("lb://clinical-service"))
				.route("communication-websocket", route -> route
						.path("/api/v1/conversations/ws")
						.uri("lb:ws://communication-service"))
				.route("communication-service", route -> route
						.path("/api/v1/conversations/**", "/api/v1/conversations")
						.uri("lb://communication-service"))
				.route("file-service", route -> route
						.path("/api/v1/files/**", "/api/v1/files")
						.uri("lb://file-service"))
				.route("notification-websocket", route -> route
						.path("/api/v1/notifications/ws")
						.uri("lb:ws://notification-service"))
				.route("notification-service", route -> route
						.path(
								"/api/v1/notifications/**",
								"/api/v1/notifications")
						.uri("lb://notification-service"))
				.build();
	}
}
