package com.sahha.scheduling.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

	@Bean
	OpenAPI sahhaSchedulingOpenApi() {
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
						.title("Sahha Scheduling Service API")
						.version("v1")
						.description(
								"Organisation-scoped doctor availability and deterministic slots. Use synthetic data only."));
	}
}
