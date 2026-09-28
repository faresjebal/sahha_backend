package com.sahha.auth.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sahha.auth.service.usersessionservice.UserSessionCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sahha.auth.client.organisation.OrganisationContextClient;
import com.sahha.auth.client.organisation.OrganisationContextResource;
import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.entity.AccountStatus;
import com.sahha.auth.entity.PlatformRoleCode;
import com.sahha.auth.entity.UserPlatformRole;
import com.sahha.auth.repository.PlatformRoleRepository;
import com.sahha.auth.repository.RefreshTokenRepository;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.repository.UserPlatformRoleRepository;
import com.sahha.auth.repository.UserSessionRepository;
import com.sahha.auth.service.useraccountservice.AccountRegistrationService;
import com.sahha.auth.service.useraccountservice.AccountVerificationService;
import com.sahha.auth.service.useraccountservice.PendingRegistrationResult;

@SpringBootTest(properties = {
		"sahha.auth.session-cache.enabled=true",
		"spring.data.redis.host=${AUTH_TEST_REDIS_HOST:localhost}",
		"spring.data.redis.port=${AUTH_TEST_REDIS_PORT:6379}",
		"spring.data.redis.password=${AUTH_TEST_REDIS_PASSWORD:}"
})
@AutoConfigureMockMvc
class BrowserSessionHttpIntegrationTests {

	private static final String PASSWORD =
			"Synthetic browser passphrase 2026!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRegistrationService registrationService;

	@Autowired
	private AccountVerificationService verificationService;

	@Autowired
	private UserSessionRepository sessionRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private PlatformRoleRepository platformRoleRepository;

	@Autowired
	private UserPlatformRoleRepository platformRoleAssignmentRepository;

	@MockitoBean
	private OrganisationContextClient organisationContextClient;

	@Autowired
	private UserSessionCacheService sessionCache;

	@ParameterizedTest
	@ValueSource(strings = {"REVOKE", "COMPROMISE", "SUSPEND", "DISABLE", "CREDENTIALS", "CONTEXT"})
	void internalSessionCheckUsesCommittedStateEvenWhenRedisIsStillActive(String change) throws Exception {
		VerifiedAccount account = verifiedAccount("fresh-session");
		MvcResult login = login(account, "Synthetic session check");
		Cookie access = responseCookie(login, "SAHHA_ACCESS_TOKEN");
		Jwt jwt = jwtDecoder.decode(access.getValue());
		UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
		assertTrue(sessionCache.isActive(sessionId, account.userId(), 1, Instant.now()));
		assertSessionCheck(access, 204);
		// Deliberately omit cache publication to reproduce a lost invalidation write.
		var session = sessionRepository.findByIdWithUser(sessionId).orElseThrow();
		switch (change) {
			case "REVOKE" -> session.revoke(Instant.now(), null, "SYNTHETIC_SECURITY_TEST");
			case "COMPROMISE" -> session.markCompromised(Instant.now(), "SYNTHETIC_SECURITY_TEST");
			case "CONTEXT" -> session.selectActiveOrganisation(UUID.randomUUID(), java.util.List.of("DOCTOR"));
			default -> {
				var user = session.getUser();
				switch (change) {
					case "SUSPEND" -> user.suspend();
					case "DISABLE" -> user.disable();
					case "CREDENTIALS" -> user.changePassword(user.getPasswordHash(), Instant.now());
					default -> throw new AssertionError(change);
				}
				userRepository.saveAndFlush(user);
			}
		}
		if (java.util.Set.of("REVOKE", "COMPROMISE", "CONTEXT").contains(change)) {
			sessionRepository.saveAndFlush(session);
		}
		assertTrue(sessionCache.isActive(sessionId, account.userId(), 1, Instant.now()),
				"The deliberately stale Redis projection must not authorize this token");
		assertSessionCheck(access, 401);
		mockMvc.perform(get("/api/v1/auth/session").cookie(access)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = {"logout", "logout-all"})
	void internalCheckRejectsCapturedCookiesAfterLogout(String action) throws Exception {
		VerifiedAccount account = verifiedAccount("logout-check");
		MvcResult first = login(account, "First synthetic browser");
		MvcResult second = login(account, "Second synthetic browser");
		Cookie captured = responseCookie(first, "SAHHA_ACCESS_TOKEN");
		Cookie other = responseCookie(second, "SAHHA_ACCESS_TOKEN");
		assertSessionCheck(captured, 204);
		CsrfExchange csrf = responseCsrf(first);
		mockMvc.perform(post("/api/v1/auth/" + action).cookie(captured, csrf.cookie())
				.header("X-XSRF-TOKEN", csrf.token())).andExpect(status().isNoContent());
		assertSessionCheck(captured, 401);
		assertSessionCheck(other, "logout-all".equals(action) ? 401 : 204);
	}

	@Test
	void internalCheckRejectsCapturedCookiesAfterPasswordReset() throws Exception {
		VerifiedAccount account = verifiedAccount("reset-check");
		Cookie captured = responseCookie(login(account, "Reset synthetic browser"), "SAHHA_ACCESS_TOKEN");
		assertSessionCheck(captured, 204);
		var reset = verificationService.issuePasswordReset(account.userId(), Instant.now());
		verificationService.resetPassword(reset.getRawToken(), "Replacement synthetic passphrase 2026!", Instant.now());
		assertSessionCheck(captured, 401);
	}

	@Test
	void internalCheckRejectsRemovedPlatformAuthorityAndUnauthenticatedProbes() throws Exception {
		VerifiedAccount account = verifiedAccount("platform-check");
		var assignment = platformRoleAssignmentRepository.saveAndFlush(UserPlatformRole.assign(
				userRepository.findById(account.userId()).orElseThrow(),
				platformRoleRepository.findByCode(PlatformRoleCode.PLATFORM_ADMIN).orElseThrow(), null));
		Cookie captured = responseCookie(login(account, "Platform synthetic browser"), "SAHHA_ACCESS_TOKEN");
		assertSessionCheck(captured, 204);
		assignment.deactivate(Instant.now());
		platformRoleAssignmentRepository.saveAndFlush(assignment);
		assertSessionCheck(captured, 401);
		mockMvc.perform(get("/api/v1/internal/auth/session-check")
				.header("X-Sahha-Session-Check", UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
	}

	@Test
	void internalCheckRequiresASingleFreshnessChallengeAndReturnsNoAccountData() throws Exception {
		Cookie access = responseCookie(login(verifiedAccount("probe-contract"), "Probe synthetic browser"), "SAHHA_ACCESS_TOKEN");
		assertSessionCheck(access, 204);
		mockMvc.perform(get("/api/v1/internal/auth/session-check").cookie(access))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/internal/auth/session-check").cookie(access)
				.header("X-Sahha-Session-Check", "invalid")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/internal/auth/session-check").cookie(access)
				.header("X-Sahha-Session-Check", UUID.randomUUID().toString(), UUID.randomUUID().toString()))
				.andExpect(status().isBadRequest());
	}

	private void assertSessionCheck(Cookie access, int expected) throws Exception {
		String challenge = UUID.randomUUID().toString();
		var result = mockMvc.perform(get("/api/v1/internal/auth/session-check").cookie(access)
				.header("X-Sahha-Session-Check", challenge)).andExpect(status().is(expected))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
		if (expected == 204) {
			result.andExpect(header().string("X-Sahha-Session-Check", challenge)).andExpect(content().string(""));
		}
		else result.andExpect(header().doesNotExist("X-Sahha-Session-Check"));
	}

	@Test
	void activeOrganisationSelectionRenewsOnlyAccessAndSurvivesRefresh()
			throws Exception {
		VerifiedAccount account = verifiedAccount("organisation-context");
		MvcResult login = login(account, "Organisation laptop");
		Cookie originalAccess = responseCookie(login, "SAHHA_ACCESS_TOKEN");
		Cookie refresh = responseCookie(login, "SAHHA_REFRESH_TOKEN");
		Cookie device = responseCookie(login, "SAHHA_DEVICE_ID");
		CsrfExchange csrf = responseCsrf(login);
		UUID organisationId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		when(organisationContextClient.resolve(
				organisationId,
				originalAccess.getValue()))
				.thenReturn(new OrganisationContextResource(
						membershipId,
						organisationId,
						"Synthetic Clinic",
						"CLINIC",
						Set.of("ORGANIZATION_ADMIN"),
						3));
		long refreshCountBefore = refreshTokenRepository.count();

		MvcResult selected = mockMvc.perform(post(
						"/api/v1/auth/active-organisation")
					.cookie(originalAccess, csrf.cookie())
					.header("X-XSRF-TOKEN", csrf.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"organisationId\":\"" + organisationId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activeOrganisationId")
						.value(organisationId.toString()))
				.andExpect(jsonPath("$.organisationRoles[0]")
						.value("ORGANIZATION_ADMIN"))
				.andExpect(jsonPath("$.membershipId")
						.value(membershipId.toString()))
				.andReturn();

		Cookie selectedAccess = responseCookie(
				selected,
				"SAHHA_ACCESS_TOKEN");
		assertEquals(
				0,
				selected.getResponse().getHeaders(HttpHeaders.SET_COOKIE)
						.stream()
						.filter(value -> value.startsWith(
								"SAHHA_REFRESH_TOKEN="))
						.count());
		assertEquals(refreshCountBefore, refreshTokenRepository.count());
		Jwt selectedJwt = jwtDecoder.decode(selectedAccess.getValue());
		assertEquals(
				organisationId.toString(),
				selectedJwt.getClaimAsString("org_id"));
		assertEquals(
				java.util.List.of("ORGANIZATION_ADMIN"),
				selectedJwt.getClaimAsStringList("org_roles"));
		UUID sessionId = UUID.fromString(JsonPath.read(
				selected.getResponse().getContentAsString(),
				"$.sessionId"));
		var persisted = sessionRepository.findById(sessionId).orElseThrow();
		assertEquals(organisationId, persisted.getActiveOrganisationId());
		assertEquals(
				java.util.List.of("ORGANIZATION_ADMIN"),
				persisted.getActiveOrganisationRoles());

		mockMvc.perform(get("/api/v1/auth/session")
					.cookie(originalAccess))
				.andExpect(status().isUnauthorized());

		CsrfExchange selectedCsrf = csrf();
		mockMvc.perform(post("/api/v1/auth/refresh")
					.cookie(refresh, device, selectedCsrf.cookie())
					.header("X-XSRF-TOKEN", selectedCsrf.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activeOrganisationId")
						.value(organisationId.toString()))
				.andExpect(jsonPath("$.organisationRoles[0]")
						.value("ORGANIZATION_ADMIN"));
	}

    @Test
    void multipleMembershipsSelectOnlyCurrentDoctorOrReceptionistPermissions() throws Exception {
        VerifiedAccount account = verifiedAccount("multi-context");
        MvcResult signedIn = login(account, "Synthetic multi-membership device");
        Cookie currentAccess = responseCookie(signedIn, "SAHHA_ACCESS_TOKEN");
        UUID doctorOrganisation = UUID.randomUUID();
        UUID receptionOrganisation = UUID.randomUUID();
        Cookie doctorAccess = null;
        for (String role : java.util.List.of("DOCTOR", "RECEPTIONIST")) {
            UUID organisation = role.equals("DOCTOR") ? doctorOrganisation : receptionOrganisation;
            UUID membership = UUID.randomUUID();
            when(organisationContextClient.resolve(organisation, currentAccess.getValue()))
                    .thenReturn(new OrganisationContextResource(membership, organisation,
                            "Synthetic " + role + " clinic", "CLINIC", Set.of(role), 1));
            CsrfExchange token = csrf();
            MvcResult selected = mockMvc.perform(post("/api/v1/auth/active-organisation")
                            .cookie(currentAccess, token.cookie()).header("X-XSRF-TOKEN", token.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"organisationId\":\"" + organisation + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeOrganisationId").value(organisation.toString()))
                    .andExpect(jsonPath("$.membershipId").value(membership.toString()))
                    .andExpect(jsonPath("$.organisationRoles.length()").value(1))
                    .andExpect(jsonPath("$.organisationRoles[0]").value(role)).andReturn();
            Cookie previousAccess = currentAccess;
            currentAccess = responseCookie(selected, "SAHHA_ACCESS_TOKEN");
            Jwt jwt = jwtDecoder.decode(currentAccess.getValue());
            assertEquals(organisation.toString(), jwt.getClaimAsString("org_id"));
            assertEquals(java.util.List.of(role), jwt.getClaimAsStringList("org_roles"));
            mockMvc.perform(get("/api/v1/auth/session").cookie(previousAccess))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/auth/session").cookie(currentAccess))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeOrganisationId").value(organisation.toString()))
                    .andExpect(jsonPath("$.organisationRoles.length()").value(1))
                    .andExpect(jsonPath("$.organisationRoles[0]").value(role));
            if (role.equals("DOCTOR")) doctorAccess = currentAccess;
        }
        assertNotNull(doctorAccess);
        mockMvc.perform(get("/api/v1/auth/session").cookie(doctorAccess))
                .andExpect(status().isUnauthorized());
    }

	@Test
	void loginRefreshAndReplayUseCookiesWithoutReturningSecrets()
			throws Exception {
		VerifiedAccount account = verifiedAccount("browser-rotation");
		CsrfExchange initialCsrf = csrf();

		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
						.cookie(initialCsrf.cookie())
						.header("X-XSRF-TOKEN", initialCsrf.token())
						.header("User-Agent", "Synthetic integration browser")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(account.email(), PASSWORD, "Laptop")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId")
						.value(account.userId().toString()))
				.andExpect(jsonPath("$.sessionId").isNotEmpty())
				.andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
				.andExpect(content().string(not(containsString(PASSWORD))))
				.andReturn();

		String loginBody = login.getResponse().getContentAsString();
		String sessionId = JsonPath.read(loginBody, "$.sessionId");
		Cookie access = responseCookie(login, "SAHHA_ACCESS_TOKEN");
		Cookie refresh = responseCookie(login, "SAHHA_REFRESH_TOKEN");
		Cookie device = responseCookie(login, "SAHHA_DEVICE_ID");
		CsrfExchange loginCsrf = responseCsrf(login);
		assertNotNull(access.getValue());
		assertNotNull(refresh.getValue());
		assertFalseContains(loginBody, access.getValue());
		assertFalseContains(loginBody, refresh.getValue());
		assertCookieFlags(login, "SAHHA_ACCESS_TOKEN", "/");
		assertCookieFlags(
				login,
				"SAHHA_REFRESH_TOKEN",
				"/api/v1/auth");

		Jwt decoded = jwtDecoder.decode(access.getValue());
		assertEquals(account.userId().toString(), decoded.getSubject());
		assertEquals(sessionId, decoded.getClaimAsString("sid"));
		assertTrue(decoded.getClaims().containsKey("cv"));
		assertTrue(decoded.getClaimAsStringList("roles").isEmpty());
		assertTrue(!decoded.hasClaim("email"));

		Cookie deliberatelyInvalidAccess = new Cookie(
				"SAHHA_ACCESS_TOKEN",
				"invalid.access.cookie");
		MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(
								deliberatelyInvalidAccess,
								refresh,
								device,
								loginCsrf.cookie())
						.header("X-XSRF-TOKEN", loginCsrf.token())
						.header("User-Agent", "Synthetic integration browser")
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionId").value(sessionId))
				.andReturn();

		Cookie replacementRefresh = responseCookie(
				rotated,
				"SAHHA_REFRESH_TOKEN");
		CsrfExchange rotatedCsrf = responseCsrf(rotated);
		assertNotEquals(refresh.getValue(), replacementRefresh.getValue());

		mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(
								refresh,
								device,
								rotatedCsrf.cookie())
						.header("X-XSRF-TOKEN", rotatedCsrf.token()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:authentication-required"))
				.andExpect(content().string(not(containsString(
						refresh.getValue()))));

		assertEquals(
				SessionStatus.COMPROMISED,
				sessionRepository.findById(UUID.fromString(sessionId))
						.orElseThrow()
						.getStatus());
	}

	@Test
	void authenticatedAccountCanReadOnlyItsAuthoritativeIdentity()
			throws Exception {
		VerifiedAccount account = verifiedAccount("current-account");
		MvcResult login = login(account, "Identity laptop");
		Cookie access = responseCookie(login, "SAHHA_ACCESS_TOKEN");

		mockMvc.perform(get("/api/v1/auth/account").cookie(access))
				.andExpect(status().isOk())
				.andExpect(header().string(
						HttpHeaders.CACHE_CONTROL,
						containsString("no-store")))
				.andExpect(jsonPath("$.id")
						.value(account.userId().toString()))
				.andExpect(jsonPath("$.email").value(account.email()))
				.andExpect(jsonPath("$.firstName").value("Synthetic"))
				.andExpect(jsonPath("$.lastName").value("Browser"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.emailVerified").value(true))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mockMvc.perform(get("/api/v1/auth/account"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void csrfIsRequiredAndWrongCredentialsUseOneGenericDenial()
			throws Exception {
		VerifiedAccount account = verifiedAccount("browser-denial");

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(
								account.email(),
								PASSWORD,
								"Laptop")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:request-forbidden"));

		CsrfExchange csrf = csrf();
		mockMvc.perform(post("/api/v1/auth/login")
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(
								account.email(),
								"Incorrect synthetic passphrase",
								"Laptop")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:authentication-required"))
				.andExpect(jsonPath("$.detail").value(
						"Authentication credentials are invalid or expired."))
				.andExpect(content().string(not(containsString(
						account.email()))));
	}

	@Test
	void currentLogoutRevokesTheSessionAndClearsCredentials()
			throws Exception {
		VerifiedAccount account = verifiedAccount("browser-logout");
		MvcResult login = login(account, "Laptop");
		String sessionId = JsonPath.read(
				login.getResponse().getContentAsString(),
				"$.sessionId");
		Cookie access = responseCookie(login, "SAHHA_ACCESS_TOKEN");
		CsrfExchange csrf = responseCsrf(login);

		mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(access))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:request-forbidden"));
		assertEquals(
				SessionStatus.ACTIVE,
				sessionRepository.findById(UUID.fromString(sessionId))
						.orElseThrow()
						.getStatus());

		MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(access, csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.token()))
				.andExpect(status().isNoContent())
				.andReturn();

		assertTrue(setCookie(logout, "SAHHA_ACCESS_TOKEN")
				.contains("Max-Age=0"));
		assertTrue(setCookie(logout, "SAHHA_REFRESH_TOKEN")
				.contains("Max-Age=0"));
		assertEquals(
				SessionStatus.REVOKED,
				sessionRepository.findById(UUID.fromString(sessionId))
						.orElseThrow()
						.getStatus());

		mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(access, csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.token()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:authentication-required"));
	}

	@Test
	void globalLogoutRevokesTwoIndependentDeviceSessions()
			throws Exception {
		VerifiedAccount account = verifiedAccount("browser-global-logout");
		MvcResult first = login(account, "Laptop");
		MvcResult second = login(account, "Phone");
		String firstId = JsonPath.read(
				first.getResponse().getContentAsString(),
				"$.sessionId");
		String secondId = JsonPath.read(
				second.getResponse().getContentAsString(),
				"$.sessionId");
		Cookie access = responseCookie(first, "SAHHA_ACCESS_TOKEN");
		CsrfExchange csrf = responseCsrf(first);

		mockMvc.perform(post("/api/v1/auth/logout-all")
						.cookie(access, csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.token()))
				.andExpect(status().isNoContent());

		assertEquals(
				SessionStatus.REVOKED,
				sessionRepository.findById(UUID.fromString(firstId))
						.orElseThrow()
						.getStatus());
		assertEquals(
				SessionStatus.REVOKED,
				sessionRepository.findById(UUID.fromString(secondId))
						.orElseThrow()
						.getStatus());
	}

	@Test
	void accessTokenContainsOnlyPersistedActivePlatformRoles()
			throws Exception {
		VerifiedAccount account = verifiedAccount("browser-role");
		platformRoleAssignmentRepository.saveAndFlush(
				UserPlatformRole.assign(
						userRepository.findById(account.userId()).orElseThrow(),
						platformRoleRepository.findByCode(
								PlatformRoleCode.PLATFORM_ADMIN)
								.orElseThrow(),
						null));

		MvcResult login = login(account, "Platform admin laptop");
		Cookie access = responseCookie(login, "SAHHA_ACCESS_TOKEN");
		Jwt decoded = jwtDecoder.decode(access.getValue());

		assertEquals(
				java.util.List.of("PLATFORM_ADMIN"),
				decoded.getClaimAsStringList("roles"));
		assertTrue(!decoded.hasClaim("organisationRoles"));
		assertTrue(!decoded.hasClaim("activeOrganisationId"));
	}

	@Test
	void currentSessionRestoresMetadataWithoutRotatingCredentials()
			throws Exception {
		VerifiedAccount account = verifiedAccount("browser-current-session");
		MvcResult login = login(account, "Laptop");
		Cookie access = responseCookie(login, "SAHHA_ACCESS_TOKEN");
		String sessionId = JsonPath.read(
				login.getResponse().getContentAsString(),
				"$.sessionId");
		long tokenCountBefore = refreshTokenRepository.count();

		MvcResult restored = mockMvc.perform(
						get("/api/v1/auth/session").cookie(access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId")
						.value(account.userId().toString()))
				.andExpect(jsonPath("$.sessionId").value(sessionId))
				.andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
				.andExpect(jsonPath("$.platformRoles").isArray())
				.andReturn();

		assertEquals(tokenCountBefore, refreshTokenRepository.count());
		assertTrue(restored.getResponse()
				.getHeaders(HttpHeaders.SET_COOKIE)
				.stream()
				.noneMatch(value ->
						value.startsWith("SAHHA_ACCESS_TOKEN=")
						|| value.startsWith("SAHHA_REFRESH_TOKEN=")));
	}

	@Test
	void accountCanListAndRevokeOnlyItsOwnDeviceSessions()
			throws Exception {
		VerifiedAccount account = verifiedAccount("browser-session-management");
		MvcResult laptop = login(account, "Laptop");
		MvcResult phone = login(account, "Phone");
		VerifiedAccount anotherAccount = verifiedAccount("browser-session-owner");
		MvcResult anotherSession = login(anotherAccount, "Other laptop");

		String laptopId = JsonPath.read(
				laptop.getResponse().getContentAsString(),
				"$.sessionId");
		String phoneId = JsonPath.read(
				phone.getResponse().getContentAsString(),
				"$.sessionId");
		String anotherSessionId = JsonPath.read(
				anotherSession.getResponse().getContentAsString(),
				"$.sessionId");
		Cookie access = responseCookie(laptop, "SAHHA_ACCESS_TOKEN");
		CsrfExchange csrf = responseCsrf(laptop);

		mockMvc.perform(get("/api/v1/auth/sessions").cookie(access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath(
						"$[?(@.sessionId == '%s')].current"
								.formatted(laptopId))
						.value(true))
				.andExpect(content().string(not(containsString(
						"Synthetic integration browser"))))
				.andExpect(content().string(not(containsString(
						"192.0.2"))));

		mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", phoneId)
						.cookie(access, csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.token()))
				.andExpect(status().isNoContent());
		assertEquals(
				SessionStatus.REVOKED,
				sessionRepository.findById(UUID.fromString(phoneId))
						.orElseThrow()
						.getStatus());
		assertEquals(
				SessionStatus.ACTIVE,
				sessionRepository.findById(UUID.fromString(laptopId))
						.orElseThrow()
						.getStatus());

		mockMvc.perform(delete(
						"/api/v1/auth/sessions/{sessionId}",
						anotherSessionId)
						.cookie(access, csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.token()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:auth-resource-not-found"));
		assertEquals(
				SessionStatus.ACTIVE,
				sessionRepository
						.findById(UUID.fromString(anotherSessionId))
						.orElseThrow()
						.getStatus());
	}

	@Test
	void passwordChangeRequiresCurrentPasswordAndRevokesEverySession()
			throws Exception {
		VerifiedAccount account = verifiedAccount("browser-password-change");
		MvcResult laptop = login(account, "Laptop");
		MvcResult phone = login(account, "Phone");
		String laptopId = JsonPath.read(
				laptop.getResponse().getContentAsString(),
				"$.sessionId");
		String phoneId = JsonPath.read(
				phone.getResponse().getContentAsString(),
				"$.sessionId");
		Cookie access = responseCookie(laptop, "SAHHA_ACCESS_TOKEN");
		CsrfExchange csrf = responseCsrf(laptop);
		String replacementPassword =
				"Replacement synthetic browser passphrase 2026!";

		mockMvc.perform(post("/api/v1/auth/password-change")
						.cookie(access, csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "currentPassword": "%s",
								  "newPassword": "%s"
								}
								""".formatted(PASSWORD, replacementPassword)))
				.andExpect(status().isNoContent());

		assertEquals(
				SessionStatus.REVOKED,
				sessionRepository.findById(UUID.fromString(laptopId))
						.orElseThrow()
						.getStatus());
		assertEquals(
				SessionStatus.REVOKED,
				sessionRepository.findById(UUID.fromString(phoneId))
						.orElseThrow()
						.getStatus());

		CsrfExchange oldPasswordCsrf = csrf();
		mockMvc.perform(post("/api/v1/auth/login")
						.cookie(oldPasswordCsrf.cookie())
						.header("X-XSRF-TOKEN", oldPasswordCsrf.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(
								account.email(),
								PASSWORD,
								"Laptop")))
				.andExpect(status().isUnauthorized());

		CsrfExchange newPasswordCsrf = csrf();
		mockMvc.perform(post("/api/v1/auth/login")
						.cookie(newPasswordCsrf.cookie())
						.header("X-XSRF-TOKEN", newPasswordCsrf.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(
								account.email(),
								replacementPassword,
								"Laptop")))
				.andExpect(status().isOk());
	}

	@Test
	void persistedPlatformAdministratorCanControlAnotherAccountLifecycle()
			throws Exception {
		VerifiedAccount administrator = verifiedAccount("account-admin");
		platformRoleAssignmentRepository.saveAndFlush(
				UserPlatformRole.assign(
						userRepository.findById(administrator.userId())
								.orElseThrow(),
						platformRoleRepository.findByCode(
								PlatformRoleCode.PLATFORM_ADMIN)
								.orElseThrow(),
						null));
		VerifiedAccount target = verifiedAccount("account-admin-target");
		MvcResult targetLogin = login(target, "Target laptop");
		String targetSessionId = JsonPath.read(
				targetLogin.getResponse().getContentAsString(),
				"$.sessionId");
		MvcResult adminLogin = login(administrator, "Admin laptop");
		Cookie adminAccess = responseCookie(
				adminLogin,
				"SAHHA_ACCESS_TOKEN");
		CsrfExchange adminCsrf = responseCsrf(adminLogin);

		mockMvc.perform(put(
						"/api/v1/auth/platform/accounts/{userId}/status",
						target.userId())
						.cookie(adminAccess, adminCsrf.cookie())
						.header("X-XSRF-TOKEN", adminCsrf.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"action":"SUSPEND"}
								"""))
				.andExpect(status().isNoContent());

		assertEquals(
				AccountStatus.SUSPENDED,
				userRepository.findById(target.userId())
						.orElseThrow()
						.getStatus());
		assertEquals(
				SessionStatus.REVOKED,
				sessionRepository.findById(
								UUID.fromString(targetSessionId))
						.orElseThrow()
						.getStatus());

		mockMvc.perform(put(
						"/api/v1/auth/platform/accounts/{userId}/status",
						target.userId())
						.cookie(adminAccess, adminCsrf.cookie())
						.header("X-XSRF-TOKEN", adminCsrf.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"action":"REACTIVATE"}
								"""))
				.andExpect(status().isNoContent());
		assertEquals(
				AccountStatus.ACTIVE,
				userRepository.findById(target.userId())
						.orElseThrow()
						.getStatus());

		MvcResult targetNewLogin = login(target, "Target replacement laptop");
		Cookie targetAccess = responseCookie(
				targetNewLogin,
				"SAHHA_ACCESS_TOKEN");
		CsrfExchange targetCsrf = responseCsrf(targetNewLogin);
		mockMvc.perform(put(
						"/api/v1/auth/platform/accounts/{userId}/status",
						administrator.userId())
						.cookie(targetAccess, targetCsrf.cookie())
						.header("X-XSRF-TOKEN", targetCsrf.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"action":"SUSPEND"}
								"""))
				.andExpect(status().isForbidden());
	}

	private MvcResult login(VerifiedAccount account, String deviceName)
			throws Exception {
		CsrfExchange csrf = csrf();
		return mockMvc.perform(post("/api/v1/auth/login")
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(
								account.email(),
								PASSWORD,
								deviceName)))
				.andExpect(status().isOk())
				.andReturn();
	}

	private CsrfExchange csrf() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andReturn();
		long csrfCookieCount = result.getResponse()
				.getHeaders(HttpHeaders.SET_COOKIE)
				.stream()
				.filter(value -> value.startsWith("XSRF-TOKEN="))
				.count();
		assertEquals(1, csrfCookieCount);
		Cookie cookie = responseCookie(result, "XSRF-TOKEN");
		String responseToken = JsonPath.read(
				result.getResponse().getContentAsString(),
				"$.token");
		assertEquals(cookie.getValue(), responseToken);
		return new CsrfExchange(cookie, responseToken);
	}

	private static CsrfExchange responseCsrf(MvcResult result) {
		Cookie cookie = responseCookie(result, "XSRF-TOKEN");
		return new CsrfExchange(cookie, cookie.getValue());
	}

	private VerifiedAccount verifiedAccount(String label) {
		Instant registeredAt = Instant.now().minusSeconds(10);
		String email = label + "-" + UUID.randomUUID() + "@example.com";
		PendingRegistrationResult registration = registrationService.register(
				email,
				PASSWORD,
				"Synthetic",
				"Browser",
				null,
				registeredAt);
		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				registeredAt.plusSeconds(1));
		return new VerifiedAccount(registration.getUserId(), email);
	}

	private static String loginBody(
			String email,
			String password,
			String deviceName) {
		return """
				{
				  "email": "%s",
				  "password": "%s",
				  "deviceName": "%s"
				}
				""".formatted(email, password, deviceName);
	}

	private static Cookie responseCookie(MvcResult result, String name) {
		String header = setCookie(result, name);
		int separator = header.indexOf(';');
		String pair = separator < 0 ? header : header.substring(0, separator);
		return new Cookie(name, pair.substring(name.length() + 1));
	}

	private static String setCookie(MvcResult result, String name) {
		return result.getResponse()
				.getHeaders(HttpHeaders.SET_COOKIE)
				.stream()
				.filter(value -> value.startsWith(name + "="))
				.findFirst()
				.orElseThrow(() -> new AssertionError(
						"missing Set-Cookie for " + name));
	}

	private static void assertCookieFlags(
			MvcResult result,
			String name,
			String path) {
		String header = setCookie(result, name);
		assertTrue(header.contains("HttpOnly"));
		assertTrue(header.contains("SameSite=Lax"));
		assertTrue(header.contains("Path=" + path));
		assertTrue(!header.contains("Secure"));
	}

	private static void assertFalseContains(String value, String secret) {
		assertTrue(!value.contains(secret));
	}

	private record VerifiedAccount(UUID userId, String email) {
	}

	private record CsrfExchange(Cookie cookie, String token) {
	}
}
