package com.sahha.auth.documentation;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void openApiJsonDocumentsEveryImplementedHttpEndpoint() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(
						MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.info.title").value(
						"Sahha Auth Service API"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/registrations'].post.operationId")
						.value("registerAccount"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/registrations'].post.security[0].csrfHeader")
						.exists())
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/email-verifications/confirm'].post.operationId")
						.value("confirmEmail"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/email-verifications/resend'].post.operationId")
						.value("resendEmailVerification"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/password-resets/request'].post.operationId")
						.value("requestPasswordReset"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/password-resets/confirm'].post.operationId")
						.value("confirmPasswordReset"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/csrf'].get.operationId")
						.value("getCsrfToken"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/login'].post.operationId")
						.value("login"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/login'].post.security[0].csrfHeader")
						.exists())
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/refresh'].post.operationId")
						.value("refreshSession"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/logout'].post.operationId")
						.value("logoutCurrentSession"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/logout-all'].post.operationId")
						.value("logoutEverywhere"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/sessions'].get.operationId")
						.value("listActiveSessions"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/session'].get.operationId")
						.value("getCurrentSession"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/account'].get.operationId")
						.value("getCurrentAccount"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/sessions/{sessionId}'].delete.operationId")
						.value("revokeOwnedSession"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/password-change'].post.operationId")
						.value("changePassword"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/platform/accounts'].get.operationId")
						.value("findPlatformAccountByEmail"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/auth/platform/accounts/{userId}/status'].put.operationId")
						.value("updateAccountStatus"))
				.andExpect(jsonPath(
						"$.components.securitySchemes.cookieAuth.in")
						.value("cookie"))
				.andExpect(jsonPath(
						"$.components.securitySchemes.csrfHeader.in")
						.value("header"));
	}

	@Test
	void swaggerUiIsPubliclyReachable() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string(
						"Location",
						"/swagger-ui/index.html"));

		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Swagger UI")));
	}

	@Test
	void jwksPublishesOnlyTheRs256PublicKey() throws Exception {
		mockMvc.perform(get("/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].alg").value("RS256"))
				.andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
				.andExpect(jsonPath("$.keys[0].n").isNotEmpty())
				.andExpect(jsonPath("$.keys[0].e").isNotEmpty())
				.andExpect(jsonPath("$.keys[0].d").doesNotExist());
	}
}
