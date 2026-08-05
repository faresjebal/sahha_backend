package com.sahha.patient.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

	@Bean
	OpenAPI sahhaPatientOpenApi() {
		return new OpenAPI()
				.components(new Components()
						.addSecuritySchemes(
								"cookieAuth",
								new SecurityScheme()
										.type(SecurityScheme.Type.APIKEY)
										.in(SecurityScheme.In.COOKIE)
										.name("SAHHA_ACCESS_TOKEN")
										.description("HttpOnly access-token cookie issued by Auth."))
						.addSecuritySchemes(
								"csrfHeader",
								new SecurityScheme()
										.type(SecurityScheme.Type.APIKEY)
										.in(SecurityScheme.In.HEADER)
										.name("X-XSRF-TOKEN")
										.description(
												"Echo the token returned by GET /api/v1/auth/csrf.")))
				.info(new Info()
						.title("Sahha Patient Service API")
						.version("v1")
						.description(
								"Tenant-scoped administrative patient registry. "
										+ "Responses deliberately exclude clinical data. "
										+ "Use synthetic data only."));
	}
}
