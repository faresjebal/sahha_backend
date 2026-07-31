package com.sahha.organisation.documentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrganisationOpenApiDocumentationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void openApiDocumentsTheThreePlatformOrganisationOperations()
			throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title")
						.value("Sahha Organisation Service API"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/platform/organisations'].post.operationId")
						.value("createOrganisation"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/platform/organisations'].get.operationId")
						.value("listOrganisations"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/platform/organisations/{organisationId}'].get.operationId")
						.value("getOrganisation"))
				.andExpect(jsonPath(
						"$.components.securitySchemes.cookieAuth").exists())
				.andExpect(jsonPath(
						"$.components.securitySchemes.csrfHeader").exists());
	}

	@Test
	void swaggerUiIsPublicForInternshipTesting() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());
	}
}
