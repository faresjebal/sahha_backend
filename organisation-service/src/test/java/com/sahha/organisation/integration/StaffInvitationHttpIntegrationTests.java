package com.sahha.organisation.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.OrganisationType;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.repository.StaffInvitationRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationMembershipService;
import com.sahha.organisation.service.organisationservice.OrganisationService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StaffInvitationHttpIntegrationTests {

	private static final String ADMIN_TOKEN = "invitation.admin.token";
	private static final String OTHER_ADMIN_TOKEN = "invitation.other-admin.token";
	private static final String TARGET_TOKEN = "invitation.target.token";
	private static final String STRANGER_TOKEN = "invitation.stranger.token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrganisationService organisationService;

	@Autowired
	private OrganisationMembershipService membershipService;

	@Autowired
	private StaffInvitationRepository invitationRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private OrganisationMembershipRoleRepository roleRepository;

	@Autowired
	private OrganisationAuditEventRepository auditRepository;

	@Autowired
	private OrganisationOutboxEventRepository outboxRepository;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private AuthAccountDirectoryClient accountDirectoryClient;

	@Test
	void invitationAcceptanceCreatesOnlyTheInvitedMembershipAndRole()
			throws Exception {
		Workspace workspace = workspace("Lifecycle", ADMIN_TOKEN);
		UUID targetUserId = UUID.randomUUID();
		String targetEmail = "synthetic.doctor@example.test";
		target(TARGET_TOKEN, targetUserId, targetEmail);
		long auditBefore = auditRepository.countByOrganisationId(
				workspace.organisationId());
		long outboxBefore = outboxRepository.count();
		Cookie csrf = csrf();

		String created = mockMvc.perform(post("/api/v1/staff-invitations")
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.header("X-Request-ID", "staff-invitation-create")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email":"Synthetic.Doctor@example.test",
							  "role":"DOCTOR"
							}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.role").value("DOCTOR"))
				.andReturn().getResponse().getContentAsString();
		String invitationId = tools.jackson.databind.json.JsonMapper.builder()
				.build().readTree(created).get("id").asText();

		mockMvc.perform(get("/api/v1/my/staff-invitations")
					.cookie(access(TARGET_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(invitationId));

		mockMvc.perform(post(
					"/api/v1/my/staff-invitations/{invitationId}/accept",
					invitationId)
					.cookie(access(TARGET_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.header("X-Request-ID", "staff-invitation-accept")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.acceptedMembershipId").isNotEmpty());

		var membership = membershipRepository.findByOrganisationIdAndUserId(
				workspace.organisationId(),
				targetUserId).orElseThrow();
		assertEquals(targetEmail, membership.getEmailSnapshot());
		assertEquals(true, roleRepository
				.existsByMembershipIdAndRoleAndActiveTrue(
						membership.getId(),
						OrganisationRole.DOCTOR));
		assertEquals(auditBefore + 2, auditRepository.countByOrganisationId(
				workspace.organisationId()));
		assertEquals(outboxBefore + 2, outboxRepository.count());
		outboxRepository.findAll().stream()
				.filter(event -> event.getOrganisationId().equals(
						workspace.organisationId()))
				.filter(event -> event.getEventType().startsWith(
						"STAFF_INVITATION_"))
				.forEach(event -> {
					assertFalse(event.getPayload().containsKey("email"));
					assertFalse(event.getPayload().containsKey("firstName"));
					assertFalse(event.getPayload().containsKey("lastName"));
				});
	}

	@Test
	void crossOrganisationAndDifferentEmailCannotObserveOrAcceptInvitation()
			throws Exception {
		Workspace first = workspace("First", ADMIN_TOKEN);
		workspace("Second", OTHER_ADMIN_TOKEN);
		UUID targetUserId = UUID.randomUUID();
		target(TARGET_TOKEN, targetUserId, "target@example.test");
		target(STRANGER_TOKEN, UUID.randomUUID(), "stranger@example.test");
		Cookie csrf = csrf();
		String invitationId = createInvitation(
				ADMIN_TOKEN,
				csrf,
				"target@example.test",
				"RECEPTIONIST");

		mockMvc.perform(get(
					"/api/v1/staff-invitations/{invitationId}",
					invitationId)
					.cookie(access(OTHER_ADMIN_TOKEN)))
				.andExpect(status().isNotFound());

		mockMvc.perform(post(
					"/api/v1/my/staff-invitations/{invitationId}/accept",
					invitationId)
					.cookie(access(STRANGER_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":0}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:staff-invitation-not-found"));

		assertEquals(false, membershipRepository
				.existsByOrganisationIdAndUserId(
						first.organisationId(),
						targetUserId));
	}

	@Test
	void lifecycleCommandsRequireCsrfVersionAndPendingState()
			throws Exception {
		workspace("Commands", ADMIN_TOKEN);
		Cookie csrf = csrf();
		String invitationId = createInvitation(
				ADMIN_TOKEN,
				csrf,
				"reception@example.test",
				"RECEPTIONIST");

		mockMvc.perform(post(
					"/api/v1/staff-invitations/{invitationId}/renew",
					invitationId)
					.cookie(access(ADMIN_TOKEN))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":0}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post(
					"/api/v1/staff-invitations/{invitationId}/renew",
					invitationId)
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":99}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:concurrent-staff-invitation-modification"));

		mockMvc.perform(post(
					"/api/v1/staff-invitations/{invitationId}/revoke",
					invitationId)
					.cookie(access(ADMIN_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVOKED"));

		assertEquals(1, invitationRepository.count());
	}

	private String createInvitation(
			String adminToken,
			Cookie csrf,
			String email,
			String role) throws Exception {
		String body = mockMvc.perform(post("/api/v1/staff-invitations")
					.cookie(access(adminToken), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"%s","role":"%s"}
							""".formatted(email, role)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return tools.jackson.databind.json.JsonMapper.builder().build()
				.readTree(body).get("id").asText();
	}

	private Workspace workspace(String prefix, String token) {
		UUID platformActor = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		OrganisationResponse organisation = organisationService.create(
				organisationRequest(prefix + " " + UUID.randomUUID()),
				platformActor,
				"staff-invitation-test-organisation");
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
				"staff-invitation-test-membership");
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token,
				userId,
				organisation.id(),
				List.of("ORGANIZATION_ADMIN")));
		return new Workspace(organisation.id(), userId);
	}

	private void target(String token, UUID userId, String email) {
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token,
				userId,
				null,
				List.of()));
		when(accountDirectoryClient.currentAccount(token)).thenReturn(
				new AuthAccountResource(
						userId,
						email,
						"Synthetic",
						"Staff",
						"ACTIVE",
						true));
	}

	private static Jwt jwt(
			String tokenValue,
			UUID userId,
			UUID organisationId,
			List<String> organisationRoles) {
		Instant now = Instant.now();
		Jwt.Builder builder = Jwt.withTokenValue(tokenValue)
				.header("alg", "RS256")
				.subject(userId.toString())
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of())
				.claim("org_roles", organisationRoles)
				.claim("token_type", "access");
		if (organisationId != null) {
			builder.claim("org_id", organisationId.toString());
		}
		return builder.build();
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
}
