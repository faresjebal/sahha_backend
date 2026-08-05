package com.sahha.organisation.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.repository.OrganisationRepository;
import com.sahha.organisation.client.auth.AuthAccountDirectoryClient;
import com.sahha.organisation.client.auth.AuthAccountResource;
import com.sahha.organisation.dto.request.CreateOrganisationRequest;
import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.entity.OrganisationType;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.service.organisationservice.OrganisationService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlatformOrganisationHttpIntegrationTests {

	private static final String PLATFORM_TOKEN = "aaa.bbb.ccc";
	private static final String USER_TOKEN = "ddd.eee.fff";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private OrganisationAuditEventRepository auditRepository;

	@Autowired
	private OrganisationOutboxEventRepository outboxRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private OrganisationService organisationService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private AuthAccountDirectoryClient accountDirectoryClient;

	@Test
	void platformAdministratorCreatesListsAndReadsAnActiveOrganisation()
			throws Exception {
		UUID actor = UUID.randomUUID();
		when(jwtDecoder.decode(PLATFORM_TOKEN)).thenReturn(
				jwt(PLATFORM_TOKEN, actor, List.of("PLATFORM_ADMIN")));
		String name = "HTTP Clinic " + UUID.randomUUID();
		Cookie csrf = csrf();

		String body = mockMvc.perform(post("/api/v1/platform/organisations")
						.cookie(access(PLATFORM_TOKEN), csrf)
						.header("X-XSRF-TOKEN", csrf.getValue())
						.header("X-Request-ID", "organisation-http-create")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(name)))
				.andExpect(status().isCreated())
				.andExpect(header().string(
						HttpHeaders.LOCATION,
						containsString("/api/v1/platform/organisations/")))
				.andExpect(jsonPath("$.name").value(name))
				.andExpect(jsonPath("$.type").value("CLINIC"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.createdBy").value(actor.toString()))
				.andExpect(content().string(not(containsString(
						PLATFORM_TOKEN))))
				.andReturn()
				.getResponse()
				.getContentAsString();
		String id = tools.jackson.databind.json.JsonMapper.builder()
				.build()
				.readTree(body)
				.get("id")
				.asText();

		mockMvc.perform(get("/api/v1/platform/organisations")
						.cookie(access(PLATFORM_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == '%s')]".formatted(id))
						.exists());

		mockMvc.perform(get(
						"/api/v1/platform/organisations/{organisationId}",
						id)
						.cookie(access(PLATFORM_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.name").value(name));

		assertEquals(1, auditRepository.countByOrganisationId(
				UUID.fromString(id)));
		assertEquals(1, outboxRepository.count());
	}

	@Test
	void authenticatedNonPlatformUserCannotReadOrCreateOrganisations()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(
				jwt(USER_TOKEN, UUID.randomUUID(), List.of()));
		long before = organisationRepository.count();
		Cookie csrf = csrf();

		mockMvc.perform(get("/api/v1/platform/organisations")
						.cookie(access(USER_TOKEN)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:request-forbidden"));

		mockMvc.perform(post("/api/v1/platform/organisations")
						.cookie(access(USER_TOKEN), csrf)
						.header("X-XSRF-TOKEN", csrf.getValue())
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(
								"Forbidden Clinic " + UUID.randomUUID())))
				.andExpect(status().isForbidden());

		assertEquals(before, organisationRepository.count());
	}

	@Test
	void missingAuthenticationAndCsrfAreRejectedBeforeCreation()
			throws Exception {
		String name = "Rejected Clinic " + UUID.randomUUID();

		mockMvc.perform(get("/api/v1/platform/organisations"))
				.andExpect(status().isUnauthorized());

		when(jwtDecoder.decode(PLATFORM_TOKEN)).thenReturn(
				jwt(
						PLATFORM_TOKEN,
						UUID.randomUUID(),
						List.of("PLATFORM_ADMIN")));
		mockMvc.perform(post("/api/v1/platform/organisations")
						.cookie(access(PLATFORM_TOKEN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(name)))
				.andExpect(status().isForbidden());
	}

	@Test
	void invalidFormReturnsSafeFieldErrorsWithoutPersisting()
			throws Exception {
		when(jwtDecoder.decode(PLATFORM_TOKEN)).thenReturn(
				jwt(
						PLATFORM_TOKEN,
						UUID.randomUUID(),
						List.of("PLATFORM_ADMIN")));
		long before = organisationRepository.count();
		Cookie csrf = csrf();

		mockMvc.perform(post("/api/v1/platform/organisations")
						.cookie(access(PLATFORM_TOKEN), csrf)
						.header("X-XSRF-TOKEN", csrf.getValue())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "",
								  "type": "CLINIC",
								  "contactEmail": "not-an-email"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:validation"))
				.andExpect(jsonPath("$.errors.name").exists())
				.andExpect(jsonPath("$.errors.contactEmail").exists())
				.andExpect(jsonPath("$.requestId").isNotEmpty());

		assertEquals(before, organisationRepository.count());
	}

	@Test
	void platformAdministratorAssignsListsAndReadsOrganisationAdministrator()
			throws Exception {
		UUID actor = UUID.randomUUID();
		UUID targetUser = UUID.randomUUID();
		String email = "organisation.admin@example.test";
		OrganisationResponse organisation = organisationService.create(
				request("Administrator API " + UUID.randomUUID()),
				actor,
				"administrator-api-organisation");
		when(jwtDecoder.decode(PLATFORM_TOKEN)).thenReturn(
				jwt(PLATFORM_TOKEN, actor, List.of("PLATFORM_ADMIN")));
		when(accountDirectoryClient.findByEmail(eq(email), eq(PLATFORM_TOKEN)))
				.thenReturn(new AuthAccountResource(
						targetUser,
						email,
						"Leila",
						"Mansour",
						"ACTIVE",
						true));
		Cookie csrf = csrf();

		String body = mockMvc.perform(post(
						"/api/v1/platform/organisations/{organisationId}/administrators",
						organisation.id())
					.cookie(access(PLATFORM_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.header("X-Request-ID", "administrator-assignment")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.organisationId")
						.value(organisation.id().toString()))
				.andExpect(jsonPath("$.userId").value(targetUser.toString()))
				.andExpect(jsonPath("$.displayName").value("Leila Mansour"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.roles[0]")
						.value("ORGANIZATION_ADMIN"))
				.andReturn()
				.getResponse()
				.getContentAsString();
		String membershipId = tools.jackson.databind.json.JsonMapper.builder()
				.build()
				.readTree(body)
				.get("id")
				.asText();

		mockMvc.perform(get(
						"/api/v1/platform/organisations/{organisationId}/administrators",
						organisation.id())
					.cookie(access(PLATFORM_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(membershipId));

		mockMvc.perform(get(
						"/api/v1/platform/organisations/{organisationId}/administrators/{membershipId}",
						organisation.id(),
						membershipId)
					.cookie(access(PLATFORM_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
		assertEquals(1, membershipRepository.count());
	}

	@Test
	void pendingAccountCannotReceiveOrganisationAdministratorMembership()
			throws Exception {
		UUID actor = UUID.randomUUID();
		String email = "pending.admin@example.test";
		OrganisationResponse organisation = organisationService.create(
				request("Pending administrator " + UUID.randomUUID()),
				actor,
				"pending-administrator-organisation");
		when(jwtDecoder.decode(PLATFORM_TOKEN)).thenReturn(
				jwt(PLATFORM_TOKEN, actor, List.of("PLATFORM_ADMIN")));
		when(accountDirectoryClient.findByEmail(eq(email), eq(PLATFORM_TOKEN)))
				.thenReturn(new AuthAccountResource(
						UUID.randomUUID(),
						email,
						"Pending",
						"User",
						"PENDING_VERIFICATION",
						false));
		Cookie csrf = csrf();

		mockMvc.perform(post(
						"/api/v1/platform/organisations/{organisationId}/administrators",
						organisation.id())
					.cookie(access(PLATFORM_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:membership-conflict"));
		assertEquals(0, membershipRepository.count());
	}

	@Test
	void nonPlatformUserCannotAssignOrganisationAdministrator()
			throws Exception {
		OrganisationResponse organisation = organisationService.create(
				request("Forbidden administrator " + UUID.randomUUID()),
				UUID.randomUUID(),
				"forbidden-administrator-organisation");
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(
				jwt(USER_TOKEN, UUID.randomUUID(), List.of()));
		Cookie csrf = csrf();

		mockMvc.perform(post(
						"/api/v1/platform/organisations/{organisationId}/administrators",
						organisation.id())
					.cookie(access(USER_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"admin@example.test\"}"))
				.andExpect(status().isForbidden());

		verifyNoInteractions(accountDirectoryClient);
		assertEquals(0, membershipRepository.count());
	}

	@Test
	void userListsAndResolvesOnlyTheirOwnActiveOrganisationContext()
			throws Exception {
		UUID platformUserId = UUID.randomUUID();
		UUID memberUserId = UUID.randomUUID();
		UUID unrelatedUserId = UUID.randomUUID();
		OrganisationResponse organisation = organisationService.create(
				request("Context clinic " + UUID.randomUUID()),
				platformUserId,
				"context-clinic-create");
		String memberEmail = "context-" + UUID.randomUUID() + "@example.test";
		when(jwtDecoder.decode(PLATFORM_TOKEN)).thenReturn(
				jwt(PLATFORM_TOKEN, platformUserId, List.of("PLATFORM_ADMIN")));
		when(accountDirectoryClient.findByEmail(
				eq(memberEmail),
				eq(PLATFORM_TOKEN)))
				.thenReturn(new AuthAccountResource(
						memberUserId,
						memberEmail,
						"Context",
						"Administrator",
						"ACTIVE",
						true));
		Cookie csrf = csrf();
		mockMvc.perform(post(
						"/api/v1/platform/organisations/{organisationId}/administrators",
						organisation.id())
					.cookie(access(PLATFORM_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + memberEmail + "\"}"))
				.andExpect(status().isCreated());

		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(
				jwt(USER_TOKEN, memberUserId, List.of()));
		mockMvc.perform(get("/api/v1/organisations/memberships")
					.cookie(access(USER_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].organisationId")
						.value(organisation.id().toString()))
				.andExpect(jsonPath("$[0].roles[0]")
						.value("ORGANIZATION_ADMIN"));
		mockMvc.perform(get(
						"/api/v1/organisations/{organisationId}/membership-context",
						organisation.id())
					.cookie(access(USER_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.organisationName")
						.value(organisation.name()));

		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(
				jwt(USER_TOKEN, unrelatedUserId, List.of()));
		mockMvc.perform(get("/api/v1/organisations/memberships")
					.cookie(access(USER_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
		mockMvc.perform(get(
						"/api/v1/organisations/{organisationId}/membership-context",
						organisation.id())
					.cookie(access(USER_TOKEN)))
				.andExpect(status().isNotFound());
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", UUID.randomUUID().toString());
	}

	private static Jwt jwt(
			String tokenValue,
			UUID userId,
			List<String> roles) {
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
				.claim("roles", roles)
				.claim("token_type", "access")
				.build();
	}

	private static String requestBody(String name) {
		return """
				{
				  "name": "%s",
				  "legalName": "%s Legal",
				  "type": "CLINIC",
				  "contactEmail": "contact@example.com",
				  "phoneNumber": "+216 71 000 000",
				  "address": "12 Synthetic Avenue",
				  "city": "Tunis",
				  "region": "Tunis",
				  "postalCode": "1000",
				  "countryCode": "TN",
				  "timeZone": "Africa/Tunis"
				}
				""".formatted(name, name);
	}

	private static CreateOrganisationRequest request(String name) {
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
}
