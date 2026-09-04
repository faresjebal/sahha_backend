package com.sahha.file.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

	@Bean
	OpenAPI sahhaFileOpenApi() {
		return new OpenAPI()
				.components(new Components()
						.addSecuritySchemes("cookieAuth", new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.COOKIE)
								.name("SAHHA_ACCESS_TOKEN"))
						.addSecuritySchemes("csrfHeader", new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.HEADER)
								.name("X-XSRF-TOKEN"))
						.addSecuritySchemes("uploadTicket", new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.HEADER)
								.name("X-Upload-Token"))
						.addSecuritySchemes("downloadGrant", new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.HEADER)
								.name("X-Download-Token")))
				.info(new Info()
						.title("Sahha File Service API")
						.version("v1")
						.description("Private medical-file workflows. Use synthetic data only."));
	}
}
