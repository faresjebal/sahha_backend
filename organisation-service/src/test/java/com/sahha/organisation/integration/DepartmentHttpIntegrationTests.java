package com.sahha.organisation.integration;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.client.auth.AuthAccountDirectoryClient;
import com.sahha.organisation.client.auth.AuthAccountResource;
import com.sahha.organisation.dto.request.CreateOrganisationRequest;
import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.entity.OrganisationType;
import com.sahha.organisation.repository.DepartmentRepository;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationMembershipService;
import com.sahha.organisation.service.organisationservice.OrganisationService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DepartmentHttpIntegrationTests {

	private static final String ADMIN_TOKEN = "department.admin.token";
	private static final String OTHER_TOKEN = "other.admin.token";
	private static final String USER_TOKEN = "ordinary.user.token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrganisationService organisationService;

	@Autowired
	private OrganisationMembershipService membershipService;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private OrganisationAuditEventRepository auditRepository;

	@Autowired
	private OrganisationOutboxEventRepository outboxRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private AuthAccountDirectoryClient accountDirectoryClient;

	@Test
	void activeOrganisationAdministratorManagesDepartmentLifecycle()
			throws Exception {
		Workspace workspace = workspace("Lifecycle", ADMIN_TOKEN);
		long auditBefore = auditRepository.countByOrganisationId(
				workspace.organisationId());
		long outboxBefore = outboxRepository.count();
		Cookie csrf = csrf();

		String createdBody = mockMvc.perform(post("/api/v1/departments")
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.header("X-Request-ID", "department-create")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("Cardiology", "CARD")))
				.andExpect(status().isCreated())
				.andExpect(header().string(
						HttpHeaders.LOCATION,
						containsString("/api/v1/departments/")))
				.andExpect(jsonPath("$.organisationId")
						.value(workspace.organisationId().toString()))
				.andExpect(jsonPath("$.name").value("Cardiology"))
				.andExpect(jsonPath("$.code").value("CARD"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.version").value(0))
				.andReturn().getResponse().getContentAsString();
		String departmentId = tools.jackson.databind.json.JsonMapper.builder()
				.build().readTree(createdBody).get("id").asText();

		mockMvc.perform(get("/api/v1/departments")
					.cookie(access(ADMIN_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(departmentId));

		mockMvc.perform(put("/api/v1/departments/{departmentId}", departmentId)
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "name": "Cardiovascular Medicine",
							  "code": "CV-MED",
							  "description": "Synthetic department data only.",
							  "version": 0
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name")
						.value("Cardiovascular Medicine"))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(put(
						"/api/v1/departments/{departmentId}/status",
						departmentId)
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"INACTIVE\",\"version\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INACTIVE"))
				.andExpect(jsonPath("$.version").value(2));

		assertEquals(1, departmentRepository.count());
		assertEquals(auditBefore + 3, auditRepository.countByOrganisationId(
				workspace.organisationId()));
		assertEquals(outboxBefore + 3, outboxRepository.count());
		outboxRepository.findAll().stream()
				.filter(event -> event.getOrganisationId().equals(
						workspace.organisationId()))
				.filter(event -> event.getEventType().startsWith("DEPARTMENT_"))
				.forEach(event -> {
					assertFalse(event.getPayload().containsKey("name"));
					assertFalse(event.getPayload().containsKey("code"));
					assertFalse(event.getPayload().containsKey("description"));
				});
	}

	@Test
	void duplicateNameOrCodeIsRejectedWithinOrganisation() throws Exception {
		workspace("Duplicate", ADMIN_TOKEN);
		Cookie csrf = csrf();
		mockMvc.perform(post("/api/v1/departments")
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("Emergency Medicine", "EM")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/departments")
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("  emergency   medicine ", "EM-2")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:department-conflict"));
	}

	@Test
	void departmentIdFromAnotherOrganisationIsHidden() throws Exception {
		Workspace first = workspace("First", ADMIN_TOKEN);
		Workspace second = workspace("Second", OTHER_TOKEN);
		Cookie csrf = csrf();
		String created = mockMvc.perform(post("/api/v1/departments")
					.cookie(access(OTHER_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("Neurology", "NEURO")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String departmentId = tools.jackson.databind.json.JsonMapper.builder()
				.build().readTree(created).get("id").asText();

		mockMvc.perform(get("/api/v1/departments/{departmentId}", departmentId)
					.cookie(access(ADMIN_TOKEN)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:department-not-found"));

		assertEquals(0, departmentRepository
				.findAllByOrganisationId(
						first.organisationId(),
						org.springframework.data.domain.Pageable.unpaged())
				.getTotalElements());
		assertEquals(1, departmentRepository
				.findAllByOrganisationId(
						second.organisationId(),
						org.springframework.data.domain.Pageable.unpaged())
				.getTotalElements());
	}

	@Test
	void ordinaryRoleAndSuspendedMembershipAreDenied() throws Exception {
		Workspace workspace = workspace("Denied", ADMIN_TOKEN);
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(jwt(
				USER_TOKEN,
				UUID.randomUUID(),
				workspace.organisationId(),
				List.of("DOCTOR")));

		mockMvc.perform(get("/api/v1/departments")
					.cookie(access(USER_TOKEN)))
				.andExpect(status().isForbidden());

		when(jwtDecoder.decode(OTHER_TOKEN)).thenReturn(platformJwt(
				OTHER_TOKEN,
				UUID.randomUUID()));
		mockMvc.perform(get("/api/v1/departments")
					.cookie(access(OTHER_TOKEN)))
				.andExpect(status().isForbidden());

		jdbcTemplate.update(
				"UPDATE organisation_membership SET status = 'SUSPENDED' WHERE organisation_id = ? AND user_id = ?",
				workspace.organisationId(),
				workspace.userId());
		entityManager.clear();
		mockMvc.perform(get("/api/v1/departments")
					.cookie(access(ADMIN_TOKEN)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:organisation-access-denied"));
	}

	@Test
	void staleVersionAndMissingCsrfAreRejected() throws Exception {
		workspace("Concurrency", ADMIN_TOKEN);
		Cookie csrf = csrf();
		String created = mockMvc.perform(post("/api/v1/departments")
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("Radiology", "RAD")))
				.andReturn().getResponse().getContentAsString();
		String departmentId = tools.jackson.databind.json.JsonMapper.builder()
				.build().readTree(created).get("id").asText();

		mockMvc.perform(put("/api/v1/departments/{departmentId}", departmentId)
					.cookie(access(ADMIN_TOKEN))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody(0)))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/departments/{departmentId}", departmentId)
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody(99)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:concurrent-department-modification"));
	}

	private Workspace workspace(String prefix, String token) {
		UUID platformActor = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		OrganisationResponse organisation = organisationService.create(
				organisationRequest(prefix + " " + UUID.randomUUID()),
				platformActor,
				"department-test-organisation");
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
				"department-test-membership");
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token,
				userId,
				organisation.id(),
				List.of("ORGANIZATION_ADMIN")));
		return new Workspace(organisation.id(), userId);
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

	private static String createBody(String name, String code) {
		return """
				{
				  "name": "%s",
				  "code": "%s",
				  "description": "Synthetic department data only."
				}
				""".formatted(name, code);
	}

	private static String updateBody(long version) {
		return """
				{
				  "name": "Diagnostic Imaging",
				  "code": "IMG",
				  "description": "Synthetic department data only.",
				  "version": %d
				}
				""".formatted(version);
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", UUID.randomUUID().toString());
	}

	private record Workspace(UUID organisationId, UUID userId) {
	}
}
