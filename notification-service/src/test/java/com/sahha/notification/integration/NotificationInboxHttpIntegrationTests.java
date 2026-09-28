package com.sahha.notification.integration;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import com.sahha.notification.client.organisation.OrganisationContextClient;
import com.sahha.notification.client.organisation.OrganisationContextResource;
import com.sahha.notification.entity.InAppNotification;
import com.sahha.notification.event.AppointmentEventSource;
import com.sahha.notification.event.AppointmentEventType;
import com.sahha.notification.event.AppointmentEventV1;
import com.sahha.notification.event.AppointmentStatus;
import com.sahha.notification.exception.NotificationAccessDeniedException;
import com.sahha.notification.exception.OrganisationContextUnavailableException;
import com.sahha.notification.repository.InAppNotificationRepository;
import com.sahha.notification.service.appointmentnotificationservice.AppointmentNotificationService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationInboxHttpIntegrationTests {

	private static final String TOKEN = "notification.user.token";
	private static final String CSRF_VALUE = "notification-csrf-token";
	private static final String TOPIC = "sahha.scheduling.appointments.v1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppointmentNotificationService appointmentNotificationService;

	@Autowired
	private InAppNotificationRepository notificationRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private OrganisationContextClient organisationContextClient;

	private long sourceOffset;

	@Autowired
	private com.sahha.notification.service.referralnotificationservice.ReferralNotificationService referralService;

	@Test
	void referralInboxRecoveryAndReadAreParticipantAndOrganisationScoped() throws Exception {
		UUID organisation = UUID.randomUUID();
		UUID user = workspace(TOKEN, organisation, "DOCTOR");
		UUID referral = UUID.randomUUID();
		for (int index = 0; index < 3; index++) {
			var event = new com.sahha.notification.event.ReferralEventV1(
					UUID.randomUUID(), "referral.sent.v1", 1, Instant.now(),
					index == 1 ? UUID.randomUUID() : organisation, referral, UUID.randomUUID(),
					List.of(index == 2 ? UUID.randomUUID() : user), "SENT", (long) index);
			referralService.consume(event, new com.sahha.notification.event.CommunicationEventSource(
					"inbox-referral-" + event.eventId(), 0, 0));
		}
		mockMvc.perform(get("/api/v1/notifications").cookie(access(TOKEN)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].resourceType").value("REFERRAL"))
				.andExpect(jsonPath("$.items[0].resourceId").value(referral.toString()))
				.andExpect(jsonPath("$.items[0].notificationType").value("REFERRAL_RECEIVED"))
				.andExpect(jsonPath("$.items[0].appointmentStatus").isEmpty())
				.andExpect(jsonPath("$.items[0].patientId").doesNotExist())
				.andExpect(jsonPath("$.items[0].recipientUserId").doesNotExist())
				.andExpect(jsonPath("$.items[0].sourceEventId").doesNotExist());
		var owned = notificationRepository.findAllByRecipientUserIdOrderByCreatedAtDesc(user).stream()
				.filter(value -> value.getOrganisationId().equals(organisation)).findFirst().orElseThrow();
		mockMvc.perform(post("/api/v1/notifications/{id}/read", owned.getId()).cookie(access(TOKEN)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/notifications/{id}/read", owned.getId())
				.cookie(access(TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF_VALUE))
				.andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
		workspace(TOKEN, organisation, "DOCTOR");
		mockMvc.perform(post("/api/v1/notifications/{id}/read", owned.getId())
				.cookie(access(TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF_VALUE))
				.andExpect(status().isNotFound());
	}

	@BeforeEach
	void resetOffset() {
		sourceOffset = 0;
	}

	@Test
	void inboxAndUnreadCountAreScopedAndDoNotLeakOwnershipColumns()
			throws Exception {
		UUID organisationId = UUID.randomUUID();
		UUID userId = workspace(TOKEN, organisationId, "DOCTOR");
		UUID ownedId = seed(organisationId, userId);
		seed(UUID.randomUUID(), userId);
		seed(organisationId, UUID.randomUUID());

		mockMvc.perform(get("/api/v1/notifications")
					.cookie(access(TOKEN))
					.param("page", "0")
					.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].id")
						.value(ownedId.toString()))
				.andExpect(jsonPath("$.items[0].read").value(false))
				.andExpect(jsonPath("$.items[0].organisationId").doesNotExist())
				.andExpect(jsonPath("$.items[0].recipientUserId").doesNotExist())
				.andExpect(jsonPath("$.items[0].sourceEventId").doesNotExist())
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/api/v1/notifications/unread-count")
					.cookie(access(TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(1));
	}

	@Test
	void markOneReadRequiresCsrfHidesForeignIdsAndIsIdempotent()
			throws Exception {
		UUID organisationId = UUID.randomUUID();
		UUID userId = workspace(TOKEN, organisationId, "RECEPTIONIST");
		UUID ownedId = seed(organisationId, userId);
		UUID foreignId = seed(organisationId, UUID.randomUUID());

		mockMvc.perform(post(
					"/api/v1/notifications/{notificationId}/read",
					ownedId)
					.cookie(access(TOKEN)))
				.andExpect(status().isForbidden());

		mockMvc.perform(post(
					"/api/v1/notifications/{notificationId}/read",
					foreignId)
					.cookie(access(TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:notification-not-found"));

		MvcResult first = mockMvc.perform(post(
					"/api/v1/notifications/{notificationId}/read",
					ownedId)
					.cookie(access(TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(true))
				.andReturn();
		String firstReadAt = objectMapper
				.readTree(first.getResponse().getContentAsString())
				.get("readAt")
				.asText();

		mockMvc.perform(post(
					"/api/v1/notifications/{notificationId}/read",
					ownedId)
					.cookie(access(TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.readAt").value(firstReadAt));
	}

	@Test
	void markAllReadChangesOnlyTheCurrentUserAndOrganisation()
			throws Exception {
		UUID organisationId = UUID.randomUUID();
		UUID userId = workspace(TOKEN, organisationId, "ORGANIZATION_ADMIN");
		seed(organisationId, userId);
		seed(organisationId, userId);
		UUID otherUserId = UUID.randomUUID();
		seed(organisationId, otherUserId);
		UUID otherOrganisationId = UUID.randomUUID();
		seed(otherOrganisationId, userId);

		mockMvc.perform(post("/api/v1/notifications/read-all")
					.cookie(access(TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updatedCount").value(2));

		org.junit.jupiter.api.Assertions.assertEquals(
				0,
				notificationRepository
						.countByOrganisationIdAndRecipientUserIdAndReadAtIsNull(
								organisationId, userId));
		org.junit.jupiter.api.Assertions.assertEquals(
				1,
				notificationRepository
						.countByOrganisationIdAndRecipientUserIdAndReadAtIsNull(
								organisationId, otherUserId));
		org.junit.jupiter.api.Assertions.assertEquals(
				1,
				notificationRepository
						.countByOrganisationIdAndRecipientUserIdAndReadAtIsNull(
								otherOrganisationId, userId));
	}

	@Test
	void authenticationPaginationAndLiveMembershipFailuresAreSafe()
			throws Exception {
		mockMvc.perform(get("/api/v1/notifications"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:authentication-required"));

		UUID organisationId = UUID.randomUUID();
		workspace(TOKEN, organisationId, "DOCTOR");
		mockMvc.perform(get("/api/v1/notifications")
					.cookie(access(TOKEN))
					.param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:invalid-notification-request"));

		doThrow(new NotificationAccessDeniedException())
				.when(organisationContextClient)
				.resolve(organisationId, TOKEN);
		mockMvc.perform(get("/api/v1/notifications")
					.cookie(access(TOKEN)))
				.andExpect(status().isForbidden());

		doThrow(new OrganisationContextUnavailableException())
				.when(organisationContextClient)
				.resolve(organisationId, TOKEN);
		mockMvc.perform(get("/api/v1/notifications")
					.cookie(access(TOKEN)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:organisation-context-unavailable"));
	}

	@Test
	void openApiDocumentsTheRecoveryEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/notifications']")
						.exists())
				.andExpect(jsonPath(
						"$.paths['/api/v1/notifications/unread-count']")
						.exists())
				.andExpect(jsonPath(
						"$.paths['/api/v1/notifications/{notificationId}/read']")
						.exists())
				.andExpect(jsonPath(
						"$.paths['/api/v1/notifications/read-all']")
						.exists());
	}

	private UUID workspace(
			String token,
			UUID organisationId,
			String role) {
		UUID userId = UUID.randomUUID();
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token, userId, organisationId, List.of(role)));
		when(organisationContextClient.resolve(organisationId, token))
				.thenReturn(new OrganisationContextResource(
						UUID.randomUUID(),
						organisationId,
						"Synthetic clinic",
						"CLINIC",
						Set.of(role),
						0));
		return userId;
	}

	private UUID seed(UUID organisationId, UUID recipientUserId) {
		UUID eventId = UUID.randomUUID();
		appointmentNotificationService.consume(
				new AppointmentEventV1(
						eventId,
						AppointmentEventType.APPOINTMENT_REQUESTED,
						1,
						Instant.now(),
						UUID.randomUUID(),
						organisationId,
						UUID.randomUUID(),
						recipientUserId,
						UUID.randomUUID(),
						"notification-inbox-test",
						AppointmentStatus.REQUESTED,
						Instant.now().plusSeconds(3_600),
						Instant.now().plusSeconds(5_400),
						"Africa/Tunis",
						"Synthetic clinic room",
						0L,
						null,
						null),
				new AppointmentEventSource(TOPIC, 0, sourceOffset++));
		return notificationRepository.findAll().stream()
				.filter(notification -> eventId.equals(
						notification.getSourceEventId()))
				.map(InAppNotification::getId)
				.findFirst()
				.orElseThrow();
	}

	private static Jwt jwt(
			String token,
			UUID userId,
			UUID organisationId,
			List<String> roles) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(token)
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
				.claim("org_roles", roles)
				.claim("token_type", "access")
				.build();
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", CSRF_VALUE);
	}
}
