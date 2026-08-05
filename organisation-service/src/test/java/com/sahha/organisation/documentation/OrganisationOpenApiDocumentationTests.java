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
	void openApiDocumentsOrganisationDepartmentInvitationAndStaffOperations()
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
						"$.paths['/api/v1/platform/organisations/{organisationId}/administrators'].post.operationId")
						.value("assignOrganisationAdministrator"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/platform/organisations/{organisationId}/administrators'].get.operationId")
						.value("listOrganisationAdministrators"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/platform/organisations/{organisationId}/administrators/{membershipId}'].get.operationId")
						.value("getOrganisationAdministrator"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/departments'].post.operationId")
						.value("createDepartment"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/departments'].get.operationId")
						.value("listDepartments"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/departments/{departmentId}'].put.operationId")
						.value("updateDepartment"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/departments/{departmentId}/status'].put.operationId")
						.value("changeDepartmentStatus"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/staff-invitations'].post.operationId")
						.value("createStaffInvitation"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/staff-invitations/{invitationId}/renew'].post.operationId")
						.value("renewStaffInvitation"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/my/staff-invitations'].get.operationId")
						.value("listMyStaffInvitations"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/my/staff-invitations/{invitationId}/accept'].post.operationId")
						.value("acceptMyStaffInvitation"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/staff'].get.operationId")
						.value("listOrganisationStaff"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/staff/{membershipId}/status'].put.operationId")
						.value("changeStaffMembershipStatus"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/staff/{membershipId}/department-assignments'].post.operationId")
						.value("assignStaffDepartment"))
				.andExpect(jsonPath(
						"$.paths['/api/v1/my/doctor-profile'].put.operationId")
						.value("upsertMyDoctorProfile"))
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
