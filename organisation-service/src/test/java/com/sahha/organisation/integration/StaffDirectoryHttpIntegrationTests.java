package com.sahha.organisation.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.client.auth.AuthAccountDirectoryClient;
import com.sahha.organisation.client.auth.AuthAccountResource;
import com.sahha.organisation.dto.request.CreateOrganisationRequest;
import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.entity.Department;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipRole;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.OrganisationType;
import com.sahha.organisation.entity.StaffDepartmentAssignmentStatus;
import com.sahha.organisation.repository.DepartmentRepository;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.repository.StaffDepartmentAssignmentRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationMembershipService;
import com.sahha.organisation.service.organisationservice.OrganisationService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StaffDirectoryHttpIntegrationTests {

	private static final String ADMIN_TOKEN = "staff.admin.token";
	private static final String OTHER_ADMIN_TOKEN = "staff.other.admin.token";
	private static final String DOCTOR_TOKEN = "staff.doctor.token";
	private static final String RECEPTIONIST_TOKEN = "staff.receptionist.token";
	private static final String PLATFORM_TOKEN = "staff.platform.token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrganisationService organisationService;

	@Autowired
	private OrganisationMembershipService membershipService;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private OrganisationMembershipRoleRepository roleRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private StaffDepartmentAssignmentRepository assignmentRepository;

	@Autowired
	private OrganisationAuditEventRepository auditRepository;

	@Autowired
	private OrganisationOutboxEventRepository outboxRepository;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private AuthAccountDirectoryClient accountDirectoryClient;

	@Test
	void administratorListsOnlyTenantStaffAndManagesDepartmentPlacement()
			throws Exception {
		Workspace workspace = workspace("Directory", ADMIN_TOKEN);
		Workspace other = workspace("Other", OTHER_ADMIN_TOKEN);
		Staff doctor = staff(
				workspace,
				"Synthetic",
				"Doctor",
				OrganisationRole.DOCTOR,
				DOCTOR_TOKEN);
		Staff foreign = staff(
				other,
				"Foreign",
				"Doctor",
				OrganisationRole.DOCTOR,
				"foreign.doctor.token");
		Department cardiology = department(
				workspace,
				"Cardiology",
				"CARD");
		Department foreignDepartment = department(
				other,
				"Neurology",
				"NEURO");
		Cookie csrf = csrf();

		mockMvc.perform(get("/api/v1/staff")
					.cookie(access(ADMIN_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].membershipId")
						.value(doctor.membershipId().toString()))
				.andExpect(jsonPath("$.items[0].roles[0]").value("DOCTOR"));

		mockMvc.perform(get("/api/v1/staff/{membershipId}",
					foreign.membershipId())
					.cookie(access(ADMIN_TOKEN)))
				.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/v1/staff/{membershipId}/status",
					foreign.membershipId())
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"SUSPENDED\",\"version\":0}"))
				.andExpect(status().isNotFound());

		String assignment = mockMvc.perform(post(
					"/api/v1/staff/{membershipId}/department-assignments",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.header("X-Request-ID", "staff-assignment-create")
					.contentType(MediaType.APPLICATION_JSON)
					.content(assignmentBody(cardiology.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.departmentId")
						.value(cardiology.getId().toString()))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.primaryAssignment").value(true))
				.andReturn().getResponse().getContentAsString();
		String assignmentId = tools.jackson.databind.json.JsonMapper.builder()
				.build().readTree(assignment).get("id").asText();

		mockMvc.perform(post(
					"/api/v1/staff/{membershipId}/department-assignments",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(assignmentBody(foreignDepartment.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:department-not-found"));

		mockMvc.perform(put(
					"/api/v1/staff/{membershipId}/department-assignments/{assignmentId}/end",
					doctor.membershipId(),
					assignmentId)
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"endDate\":\"%s\",\"version\":0}"
							.formatted(LocalDate.now())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ENDED"));

		assertEquals(1, assignmentRepository.count());
		assertEquals(2, auditRepository.findAll().stream()
				.filter(event -> workspace.organisationId().equals(
						event.getOrganisationId()))
				.filter(event -> "DEPARTMENT_ASSIGNMENT".equals(
						event.getResourceType()))
				.count());
	}

	@Test
	void doctorOwnsProfileWhileReceptionistAndAdministratorCannotEditIt()
			throws Exception {
		Workspace workspace = workspace("Profiles", ADMIN_TOKEN);
		Staff doctor = staff(
				workspace,
				"Synthetic",
				"Cardiologist",
				OrganisationRole.DOCTOR,
				DOCTOR_TOKEN);
		staff(
				workspace,
				"Synthetic",
				"Receptionist",
				OrganisationRole.RECEPTIONIST,
				RECEPTIONIST_TOKEN);
		Cookie csrf = csrf();

		mockMvc.perform(put("/api/v1/my/doctor-profile")
					.cookie(access(DOCTOR_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.header("X-Request-ID", "doctor-profile-create")
					.contentType(MediaType.APPLICATION_JSON)
					.content(profileBody("MED-12345", "null")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.membershipId")
						.value(doctor.membershipId().toString()))
				.andExpect(jsonPath("$.specialty").value("Cardiology"))
				.andExpect(jsonPath("$.version").value(0));

		mockMvc.perform(get("/api/v1/my/doctor-profile")
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.licenceNumber").value("MED-12345"));

		mockMvc.perform(put("/api/v1/my/doctor-profile")
					.cookie(access(DOCTOR_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.header("X-Request-ID", "doctor-profile-update")
					.contentType(MediaType.APPLICATION_JSON)
					.content(profileBody("MED-12345", "0")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(get("/api/v1/staff/{membershipId}/doctor-profile",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.professionalTitle")
						.value("Consultant cardiologist"))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(put("/api/v1/my/doctor-profile")
					.cookie(access(RECEPTIONIST_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(profileBody("REC-1", "null")))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/my/doctor-profile")
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(profileBody("ADM-1", "null")))
				.andExpect(status().isForbidden());

		var profileEvents = outboxRepository.findAll().stream()
				.filter(event -> "DOCTOR_PROFILE_CREATED".equals(
						event.getEventType())
						|| "DOCTOR_PROFILE_UPDATED".equals(
								event.getEventType()))
				.toList();
		assertEquals(2, profileEvents.size());
		profileEvents
				.stream()
				.forEach(event -> {
					assertFalse(event.getPayload().containsKey("specialty"));
					assertFalse(event.getPayload().containsKey("licenceNumber"));
					assertFalse(event.getPayload().containsKey(
							"registrationAuthority"));
					assertFalse(event.getPayload().containsKey("biography"));
				});
	}

	@Test
	void suspensionIsImmediatelyEnforcedAndRemovalCannotBeReversed()
			throws Exception {
		Workspace workspace = workspace("Lifecycle", ADMIN_TOKEN);
		Staff doctor = staff(
				workspace,
				"Synthetic",
				"Doctor",
				OrganisationRole.DOCTOR,
				DOCTOR_TOKEN);
		Department department = department(
				workspace,
				"Internal Medicine",
				"INTERNAL");
		Cookie csrf = csrf();
		mockMvc.perform(post(
					"/api/v1/staff/{membershipId}/department-assignments",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(assignmentBody(department.getId())))
				.andExpect(status().isCreated());

		mockMvc.perform(put("/api/v1/staff/{membershipId}/status",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"SUSPENDED\",\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUSPENDED"))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(get("/api/v1/my/doctor-profile")
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:organisation-access-denied"));

		mockMvc.perform(put("/api/v1/staff/{membershipId}/status",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"ACTIVE\",\"version\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		mockMvc.perform(put("/api/v1/staff/{membershipId}/status",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"REMOVED\",\"version\":2}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REMOVED"));

		mockMvc.perform(put("/api/v1/staff/{membershipId}/status",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"ACTIVE\",\"version\":3}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:staff-management-conflict"));

		assertEquals(false, roleRepository
				.existsByMembershipIdAndRoleAndActiveTrue(
						doctor.membershipId(),
						OrganisationRole.DOCTOR));
		assertEquals(
				StaffDepartmentAssignmentStatus.ENDED,
				assignmentRepository.findAllByMembershipIdAndStatus(
						doctor.membershipId(),
						StaffDepartmentAssignmentStatus.ENDED)
						.getFirst().getStatus());
		assertEquals(1, auditRepository.findAll().stream()
				.filter(event -> workspace.organisationId().equals(
						event.getOrganisationId()))
				.filter(event -> "DEPARTMENT_ASSIGNMENT".equals(
						event.getResourceType()))
				.filter(event -> "STAFF_DEPARTMENT_ASSIGNMENT_ENDED".equals(
						event.getEventType().name()))
				.count());
	}

	@Test
	void doctorLicenceNumberIsUniqueInsideTheOrganisation() throws Exception {
		Workspace workspace = workspace("Licence", ADMIN_TOKEN);
		staff(
				workspace,
				"First",
				"Doctor",
				OrganisationRole.DOCTOR,
				DOCTOR_TOKEN);
		staff(
				workspace,
				"Second",
				"Doctor",
				OrganisationRole.DOCTOR,
				"second.doctor.token");
		Cookie csrf = csrf();

		mockMvc.perform(put("/api/v1/my/doctor-profile")
					.cookie(access(DOCTOR_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(profileBody("ORG-LICENCE-1", "null")))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/my/doctor-profile")
					.cookie(access("second.doctor.token"), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(profileBody(" org-licence-1 ", "null")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:staff-management-conflict"));
	}

	@Test
	void staffAdministrationRequiresAdministratorRoleCsrfAndCurrentVersion()
			throws Exception {
		Workspace workspace = workspace("Boundaries", ADMIN_TOKEN);
		Staff doctor = staff(
				workspace,
				"Synthetic",
				"Doctor",
				OrganisationRole.DOCTOR,
				DOCTOR_TOKEN);
		when(jwtDecoder.decode(PLATFORM_TOKEN)).thenReturn(platformJwt(
				PLATFORM_TOKEN,
				UUID.randomUUID()));

		mockMvc.perform(get("/api/v1/staff")
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/staff")
					.cookie(access(PLATFORM_TOKEN)))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/staff/{membershipId}/status",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"SUSPENDED\",\"version\":0}"))
				.andExpect(status().isForbidden());

		Cookie csrf = csrf();
		mockMvc.perform(put("/api/v1/staff/{membershipId}/status",
					doctor.membershipId())
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"SUSPENDED\",\"version\":99}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:concurrent-staff-resource-modification"));
	}

	private Workspace workspace(String prefix, String token) {
		UUID platformActor = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		OrganisationResponse organisation = organisationService.create(
				organisationRequest(prefix + " " + UUID.randomUUID()),
				platformActor,
				"staff-directory-test-organisation");
		membershipService.assignAdministrator(
				organisation.id(),
				new AuthAccountResource(
						userId,
						prefix.toLowerCase() + "@example.test",
						prefix,
						"Administrator",
						"ACTIVE",
						true),
				platformActor,
				"staff-directory-test-administrator");
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token,
				userId,
				organisation.id(),
				List.of("ORGANIZATION_ADMIN")));
		return new Workspace(organisation.id(), userId);
	}

	private Staff staff(
			Workspace workspace,
			String firstName,
			String lastName,
			OrganisationRole role,
			String token) {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now().minusSeconds(30);
		OrganisationMembership membership = membershipRepository.saveAndFlush(
				OrganisationMembership.activate(
						workspace.organisationId(),
						userId,
						firstName.toLowerCase() + "."
								+ lastName.toLowerCase() + "@example.test",
						firstName,
						lastName,
						workspace.userId(),
						now));
		roleRepository.saveAndFlush(OrganisationMembershipRole.assign(
				membership,
				role,
				workspace.userId(),
				now));
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token,
				userId,
				workspace.organisationId(),
				List.of(role.name())));
		return new Staff(membership.getId(), userId);
	}

	private Department department(
			Workspace workspace,
			String name,
			String code) {
		return departmentRepository.saveAndFlush(Department.create(
				workspace.organisationId(),
				name,
				code,
				"Synthetic department data only.",
				workspace.userId(),
				Instant.now()));
	}

	private static String assignmentBody(UUID departmentId) {
		return """
				{
				  "departmentId":"%s",
				  "positionTitle":"Attending physician",
				  "primaryAssignment":true,
				  "startDate":"%s",
				  "plannedEndDate":null
				}
				""".formatted(departmentId, LocalDate.now());
	}

	private static String profileBody(String licenceNumber, String version) {
		return """
				{
				  "specialty":"Cardiology",
				  "professionalTitle":"Consultant cardiologist",
				  "licenceNumber":"%s",
				  "registrationAuthority":"Synthetic Medical Council",
				  "biography":"Synthetic internship profile.",
				  "version":%s
				}
				""".formatted(licenceNumber, version);
	}

	private static Jwt jwt(
			String tokenValue,
			UUID userId,
			UUID organisationId,
			List<String> organisationRoles) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(tokenValue)
				.header("alg", "RS256")
				.subject(userId.toString())
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of())
				.claim("org_id", organisationId.toString())
				.claim("org_roles", organisationRoles)
				.claim("token_type", "access")
				.build();
	}

	private static Jwt platformJwt(String tokenValue, UUID userId) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(tokenValue)
				.header("alg", "RS256")
				.subject(userId.toString())
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of("PLATFORM_ADMIN"))
				.claim("org_roles", List.of())
				.claim("token_type", "access")
				.build();
	}

	private static CreateOrganisationRequest organisationRequest(String name) {
		return new CreateOrganisationRequest(
				name,
				name + " Legal",
				OrganisationType.CLINIC,
				"contact@example.test",
				"+216 71 000 000",
				"12 Synthetic Avenue",
				"Tunis",
				"Tunis",
				"1000",
				"TN",
				"Africa/Tunis");
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", UUID.randomUUID().toString());
	}

	private record Workspace(UUID organisationId, UUID userId) {
	}

	private record Staff(UUID membershipId, UUID userId) {
	}
}
