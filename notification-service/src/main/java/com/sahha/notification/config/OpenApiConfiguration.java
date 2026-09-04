package com.sahha.notification.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

	@Bean
	OpenAPI sahhaNotificationOpenApi() {
		return new OpenAPI()
				.components(new Components()
						.addSecuritySchemes(
								"cookieAuth",
								new SecurityScheme()
										.type(SecurityScheme.Type.APIKEY)
										.in(SecurityScheme.In.COOKIE)
										.name("SAHHA_ACCESS_TOKEN"))
						.addSecuritySchemes(
								"csrfHeader",
								new SecurityScheme()
										.type(SecurityScheme.Type.APIKEY)
										.in(SecurityScheme.In.HEADER)
										.name("X-XSRF-TOKEN")))
				.info(new Info()
						.title("Sahha Notification Service API")
						.version("v1")
						.description(
								"Active-organisation-scoped in-app notification recovery APIs. Use synthetic data only."));
	}
}
