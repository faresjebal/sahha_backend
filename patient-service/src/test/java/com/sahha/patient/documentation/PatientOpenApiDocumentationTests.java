package com.sahha.patient.documentation;

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
class PatientOpenApiDocumentationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void openApiDocumentsAdministrativePatientOperations() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title")
						.value("Sahha Patient Service API"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/patients/duplicate-check'].post.operationId")
						.value("checkPatientDuplicates"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/patients'].post.operationId")
						.value("createPatientRegistration"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/patients'].get.operationId")
						.value("listPatientRegistrations"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/patients/{registrationId}'].get.operationId")
						.value("getPatientRegistration"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/patients/{registrationId}'].put.operationId")
						.value("updatePatientRegistration"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/patients/{registrationId}/history'].get.operationId")
						.value("listPatientAdministrativeHistory"))
				.andExpect(jsonPath("$.components.securitySchemes.cookieAuth")
						.exists())
				.andExpect(jsonPath("$.components.securitySchemes.csrfHeader")
						.exists());
	}
}
