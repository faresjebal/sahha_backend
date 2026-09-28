package com.sahha.gateway.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import com.sahha.gateway.filter.GatewayRequestContextFilter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityRoutingIntegrationTests {

	private static final String VALID_TOKEN = "aaa.bbb.ccc";
	private static final String INVALID_TOKEN = "ddd.eee.fff";
	private static final String USER_TOKEN = "ggg.hhh.iii";
	private static final AtomicInteger UPSTREAM_REQUESTS =
			new AtomicInteger();
	private static final AtomicReference<Headers> LAST_UPSTREAM_HEADERS =
			new AtomicReference<>();
	private static final AtomicReference<String> LAST_UPSTREAM_PATH =
			new AtomicReference<>();
	private static final CopyOnWriteArrayList<String> UPSTREAM_COOKIES =
			new CopyOnWriteArrayList<>();
	private static final HttpServer AUTH_SERVER = startAuthServer();
	private static final HttpServer ORGANISATION_SERVER =
			startOrganisationServer();

	@Autowired
	private WebTestClient webTestClient;

	@MockitoBean
	private ReactiveJwtDecoder jwtDecoder;

	@DynamicPropertySource
	static void authServiceInstance(DynamicPropertyRegistry registry) {
		registry.add(
				"spring.cloud.discovery.client.simple.instances.auth-service[0].uri",
				() -> "http://127.0.0.1:"
						+ AUTH_SERVER.getAddress().getPort());
		registry.add(
				"spring.cloud.discovery.client.simple.instances.organisation-service[0].uri",
				() -> "http://127.0.0.1:"
						+ ORGANISATION_SERVER.getAddress().getPort());
		registry.add(
				"spring.cloud.discovery.client.simple.instances.patient-service[0].uri",
				() -> "http://127.0.0.1:"
						+ ORGANISATION_SERVER.getAddress().getPort());
		registry.add(
				"spring.cloud.discovery.client.simple.instances.scheduling-service[0].uri",
				() -> "http://127.0.0.1:"
						+ ORGANISATION_SERVER.getAddress().getPort());
		registry.add(
				"spring.cloud.discovery.client.simple.instances.clinical-service[0].uri",
				() -> "http://127.0.0.1:"
						+ ORGANISATION_SERVER.getAddress().getPort());
		registry.add(
				"spring.cloud.discovery.client.simple.instances.communication-service[0].uri",
				() -> "http://127.0.0.1:"
						+ ORGANISATION_SERVER.getAddress().getPort());
		registry.add(
				"spring.cloud.discovery.client.simple.instances.notification-service[0].uri",
				() -> "http://127.0.0.1:"
						+ ORGANISATION_SERVER.getAddress().getPort());
		registry.add(
				"spring.cloud.discovery.client.simple.instances.file-service[0].uri",
				() -> "http://127.0.0.1:"
						+ ORGANISATION_SERVER.getAddress().getPort());
	}

	@BeforeEach
	void resetUpstreamObservations() {
		UPSTREAM_REQUESTS.set(0);
		LAST_UPSTREAM_HEADERS.set(null);
		LAST_UPSTREAM_PATH.set(null);
		UPSTREAM_COOKIES.clear();
	}

	@AfterAll
	static void stopAuthServer() {
		AUTH_SERVER.stop(0);
		ORGANISATION_SERVER.stop(0);
	}

	@Test
	void publicAuthRouteIgnoresStaleAccessAndSanitizesEdgeHeaders()
			throws Exception {
		webTestClient.get()
				.uri("/api/v1/auth/csrf")
				.cookie("SAHHA_ACCESS_TOKEN", "expired-or-malformed")
				.header(
						GatewayRequestContextFilter.REQUEST_ID_HEADER,
						"request-12345")
				.header(
						GatewayRequestContextFilter.CLIENT_IP_HEADER,
						"203.0.113.250")
				.header("Authorization", "Bearer attacker-token")
				.header("X-Sahha-User-Id", "attacker-user")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(
						GatewayRequestContextFilter.REQUEST_ID_HEADER,
						"request-12345")
				.expectHeader().valueEquals(
						HttpHeaders.SET_COOKIE,
						"XSRF-TOKEN=synthetic-csrf; Path=/")
				.expectHeader().valueEquals(
						"Content-Security-Policy",
						"default-src 'none'; frame-ancestors 'none'; base-uri 'none'")
				.expectBody().json("{\"token\":\"synthetic-csrf\"}");

		org.junit.jupiter.api.Assertions.assertEquals(
				"request-12345",
				LAST_UPSTREAM_HEADERS.get().getFirst("X-Request-ID"));
		org.junit.jupiter.api.Assertions.assertNull(
				LAST_UPSTREAM_HEADERS.get().getFirst("Authorization"));
		org.junit.jupiter.api.Assertions.assertNull(
				LAST_UPSTREAM_HEADERS.get().getFirst("X-Sahha-User-Id"));
		org.junit.jupiter.api.Assertions.assertNotEquals(
				"203.0.113.250",
				LAST_UPSTREAM_HEADERS.get().getFirst(
						GatewayRequestContextFilter.CLIENT_IP_HEADER));
		org.junit.jupiter.api.Assertions.assertNotNull(
				LAST_UPSTREAM_HEADERS.get().getFirst(
						GatewayRequestContextFilter.CLIENT_IP_HEADER));

		webTestClient.get()
				.uri("/api/v1/auth/csrf")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(
						HttpHeaders.SET_COOKIE,
						"XSRF-TOKEN=synthetic-csrf; Path=/");

		verifyNoInteractions(jwtDecoder);
		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/auth/csrf",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of(
						"SAHHA_ACCESS_TOKEN=expired-or-malformed",
						"<none>"),
				UPSTREAM_COOKIES);
	}

	@Test
	void protectedAuthRouteRequiresAValidAccessCookie()
			throws Exception {
		webTestClient.post()
				.uri("/api/v1/auth/logout")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().valueEquals(
						HttpHeaders.WWW_AUTHENTICATE, "Bearer")
				.expectBody()
				.jsonPath("$.type").isEqualTo(
						"urn:sahha:problem:authentication-required")
				.jsonPath("$.requestId").isNotEmpty();

		org.junit.jupiter.api.Assertions.assertEquals(
				0,
				UPSTREAM_REQUESTS.get());
	}

	@Test
	void validCookieIsDecodedAndTheProtectedRequestIsForwarded()
			throws Exception {
		when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(Mono.just(
				validJwt(VALID_TOKEN)));

		webTestClient.post()
				.uri("/api/v1/auth/logout")
				.cookie("SAHHA_ACCESS_TOKEN", VALID_TOKEN)
				.exchange()
				.expectStatus().isNoContent();

		verify(jwtDecoder).decode(VALID_TOKEN);
		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/auth/logout",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				1,
				UPSTREAM_REQUESTS.get());
	}

	@Test
	void invalidSignatureOrClaimsAreRejectedBeforeRouting()
			throws Exception {
		when(jwtDecoder.decode(INVALID_TOKEN)).thenReturn(Mono.error(
				new JwtValidationException(
						"synthetic invalid token",
						List.of(new OAuth2Error(
								"invalid_token",
								"synthetic invalid token",
								null)))));

		webTestClient.post()
				.uri("/api/v1/auth/logout")
				.cookie("SAHHA_ACCESS_TOKEN", INVALID_TOKEN)
				.exchange()
				.expectStatus().isUnauthorized()
				.expectBody().jsonPath("$.status").isEqualTo(401);

		verify(jwtDecoder).decode(INVALID_TOKEN);
		org.junit.jupiter.api.Assertions.assertEquals(
				0,
				UPSTREAM_REQUESTS.get());
	}

	@Test
	void platformOrganisationRouteEnforcesRoleBeforeForwarding()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.get()
				.uri("/api/v1/platform/organisations")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isForbidden()
				.expectBody().jsonPath("$.type").isEqualTo(
						"urn:sahha:problem:request-forbidden");
		org.junit.jupiter.api.Assertions.assertEquals(
				0,
				UPSTREAM_REQUESTS.get());

		when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(Mono.just(
				validJwt(VALID_TOKEN, List.of("PLATFORM_ADMIN"))));
		webTestClient.get()
				.uri("/api/v1/platform/organisations")
				.cookie("SAHHA_ACCESS_TOKEN", VALID_TOKEN)
				.exchange()
				.expectStatus().isOk()
				.expectBody().json(
						"{\"items\":[],\"totalElements\":0}");

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/platform/organisations",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + VALID_TOKEN),
				UPSTREAM_COOKIES);
		org.junit.jupiter.api.Assertions.assertEquals(
				1,
				UPSTREAM_REQUESTS.get());
	}

	@Test
	void nestedOrganisationAdministratorRouteIsProtectedAndForwarded()
			throws Exception {
		when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(Mono.just(
				validJwt(VALID_TOKEN, List.of("PLATFORM_ADMIN"))));
		UUID organisationId = UUID.randomUUID();

		webTestClient.get()
				.uri(
						"/api/v1/platform/organisations/{organisationId}/administrators",
						organisationId)
				.cookie("SAHHA_ACCESS_TOKEN", VALID_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/platform/organisations/" + organisationId
						+ "/administrators",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + VALID_TOKEN),
				UPSTREAM_COOKIES);
	}

	@Test
	void authenticatedOrganisationContextRouteIsForwardedWithoutPlatformRole()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.get()
				.uri("/api/v1/organisations/memberships")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/organisations/memberships",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + USER_TOKEN),
				UPSTREAM_COOKIES);
	}

	@Test
	void authenticatedDepartmentRouteIsForwardedToOrganisationService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.get()
				.uri("/api/v1/departments")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/departments",
				LAST_UPSTREAM_PATH.get());
	}

	@Test
	void authenticatedStaffInvitationRoutesAreForwardedToOrganisationService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.get()
				.uri("/api/v1/my/staff-invitations")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/my/staff-invitations",
				LAST_UPSTREAM_PATH.get());

		webTestClient.get()
				.uri("/api/v1/staff-invitations")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/staff-invitations",
				LAST_UPSTREAM_PATH.get());
	}

	@Test
	void authenticatedStaffDirectoryAndDoctorProfileRoutesAreForwarded()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.get()
				.uri("/api/v1/staff")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();
		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/staff",
				LAST_UPSTREAM_PATH.get());

		webTestClient.get()
				.uri("/api/v1/my/doctor-profile")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();
		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/my/doctor-profile",
				LAST_UPSTREAM_PATH.get());
	}

	@Test
	void authenticatedPatientRegistryRouteIsForwardedToPatientService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.get()
				.uri("/api/v1/patients")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/patients",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + USER_TOKEN),
				UPSTREAM_COOKIES);
	}

	@Test
	void authenticatedAvailabilityRouteIsForwardedToSchedulingService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.get()
				.uri("/api/v1/availability/doctors")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/availability/doctors",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + USER_TOKEN),
				UPSTREAM_COOKIES);
	}

	@Test
	void authenticatedAppointmentRouteIsForwardedToSchedulingService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.post()
				.uri("/api/v1/appointments")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/appointments",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + USER_TOKEN),
				UPSTREAM_COOKIES);
	}

	@Test
	void authenticatedConsultationRouteIsForwardedToClinicalService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.post()
				.uri("/api/v1/consultations")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/consultations",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + USER_TOKEN),
				UPSTREAM_COOKIES);
	}

	@Test
	void authenticatedClinicalSummaryRouteIsForwardedToClinicalService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));
		UUID patientRegistrationId = UUID.randomUUID();

		webTestClient.get()
				.uri("/api/v1/clinical/patients/{id}/summary",
						patientRegistrationId)
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/clinical/patients/" + patientRegistrationId + "/summary",
				LAST_UPSTREAM_PATH.get());
	}

	@Test
	void referralSourceDiscoveryRequiresAuthenticationAndUsesTheClinicalRoute() {
		webTestClient.get().uri("/api/v1/consultations/referral-sources?page=1&size=20")
				.exchange().expectStatus().isUnauthorized();
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(validJwt(USER_TOKEN, List.of("DOCTOR"))));
		webTestClient.get().uri("/api/v1/consultations/referral-sources?page=1&size=20")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN).exchange().expectStatus().isOk();
		org.junit.jupiter.api.Assertions.assertEquals("/api/v1/consultations/referral-sources", LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(List.of("SAHHA_ACCESS_TOKEN=" + USER_TOKEN), UPSTREAM_COOKIES);
	}

	@Test
	void authenticatedConversationRouteIsForwardedToCommunicationService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.get()
				.uri("/api/v1/conversations")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/conversations", LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + USER_TOKEN), UPSTREAM_COOKIES);
	}

	@Test
	void authenticatedReferralRouteIsForwardedToCommunicationService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of("DOCTOR"))));

		webTestClient.get()
				.uri("/api/v1/referrals")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/referrals", LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + USER_TOKEN), UPSTREAM_COOKIES);
	}

	@Test
	void authenticatedFileUploadRouteIsForwardedToFileService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of("DOCTOR"))));

		webTestClient.put()
				.uri("/api/v1/files/{fileId}/content", UUID.randomUUID())
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.header("X-Upload-Token", "one-time-ticket")
				.contentType(MediaType.APPLICATION_PDF)
				.bodyValue("synthetic")
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertTrue(
				LAST_UPSTREAM_PATH.get().startsWith("/api/v1/files/"));
		org.junit.jupiter.api.Assertions.assertEquals(
				"one-time-ticket",
				LAST_UPSTREAM_HEADERS.get().getFirst("X-Upload-Token"));
	}

	@Test
	void authenticatedFileDownloadGrantIsForwardedToFileService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of("DOCTOR"))));
		UUID fileId = UUID.randomUUID();

		webTestClient.get()
				.uri("/api/v1/files/{fileId}/content", fileId)
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.header("X-Download-Token", "one-time-download-grant")
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/files/" + fileId + "/content",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				"one-time-download-grant",
				LAST_UPSTREAM_HEADERS.get().getFirst("X-Download-Token"));
	}

	@Test
	void authenticatedNotificationRouteIsForwardedToNotificationService()
			throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(
				validJwt(USER_TOKEN, List.of())));

		webTestClient.get()
				.uri("/api/v1/notifications/unread-count")
				.cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN)
				.exchange()
				.expectStatus().isOk();

		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/v1/notifications/unread-count",
				LAST_UPSTREAM_PATH.get());
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("SAHHA_ACCESS_TOKEN=" + USER_TOKEN),
				UPSTREAM_COOKIES);
	}

	@Test
	void configuredFrontendReceivesCredentialedCorsPreflight()
			throws Exception {
		webTestClient.options()
				.uri("/api/v1/auth/login")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(
						HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
						"POST")
				.header(
						HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
						"content-type,x-xsrf-token")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(
						HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://localhost:5173")
				.expectHeader().valueEquals(
						HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
						"true");

		verifyNoInteractions(jwtDecoder);
		org.junit.jupiter.api.Assertions.assertEquals(
				0,
				UPSTREAM_REQUESTS.get());
	}

	@Test
	void configuredFrontendMaySendTheOneTimeUploadHeader()
			throws Exception {
		webTestClient.options()
				.uri("/api/v1/files/{fileId}/content", UUID.randomUUID())
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
						"content-type,x-xsrf-token,x-upload-token")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(
						HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://localhost:5173")
				.expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						value -> org.junit.jupiter.api.Assertions.assertTrue(
								value.toLowerCase().contains("x-upload-token")));

		verifyNoInteractions(jwtDecoder);
		org.junit.jupiter.api.Assertions.assertEquals(0, UPSTREAM_REQUESTS.get());
	}

	@Test
	void configuredFrontendMaySendTheOneTimeDownloadHeader()
			throws Exception {
		webTestClient.options()
				.uri("/api/v1/files/{fileId}/content", UUID.randomUUID())
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
						"x-download-token")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(
						HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://localhost:5173")
				.expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						value -> org.junit.jupiter.api.Assertions.assertTrue(
								value.toLowerCase().contains("x-download-token")));

		verifyNoInteractions(jwtDecoder);
		org.junit.jupiter.api.Assertions.assertEquals(0, UPSTREAM_REQUESTS.get());
	}

	private static Jwt validJwt(String tokenValue) {
		return validJwt(tokenValue, List.of("PLATFORM_ADMIN"));
	}

    @Test
    void organisationScopedPlatformRoleAndPermissionClaimsCannotCrossThePlatformBoundary() {
        Jwt base = validJwt(USER_TOKEN, List.of());
        Jwt forged = Jwt.withTokenValue(USER_TOKEN).headers(h -> h.putAll(base.getHeaders()))
                .claims(claims -> {
                    claims.putAll(base.getClaims());
                    claims.put("org_id", UUID.randomUUID().toString());
                    claims.put("org_roles", List.of("PLATFORM_ADMIN", "ORGANIZATION_ADMIN"));
                    claims.put("permissions", List.of("gateway:platform:route"));
                    claims.put("scope", "gateway:platform:route");
                }).build();
        when(jwtDecoder.decode(USER_TOKEN)).thenReturn(Mono.just(forged));
        webTestClient.get().uri("/api/v1/platform/organisations")
                .cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN).exchange().expectStatus().isForbidden();
        org.junit.jupiter.api.Assertions.assertEquals(0, UPSTREAM_REQUESTS.get());
    }

	private static Jwt validJwt(
			String tokenValue,
			List<String> roles) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(tokenValue)
				.header("alg", "RS256")
				.subject(UUID.randomUUID().toString())
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

	private static HttpServer startAuthServer() {
		try {
			HttpServer server = HttpServer.create(
					new InetSocketAddress(
							InetAddress.getLoopbackAddress(),
							0),
					0);
			server.createContext("/", GatewaySecurityRoutingIntegrationTests::
					handleAuthRequest);
			server.start();
			return server;
		}
		catch (IOException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}

	private static HttpServer startOrganisationServer() {
		try {
			HttpServer server = HttpServer.create(
					new InetSocketAddress(
							InetAddress.getLoopbackAddress(),
							0),
					0);
			server.createContext(
					"/",
					GatewaySecurityRoutingIntegrationTests::
							handleOrganisationRequest);
			server.start();
			return server;
		}
		catch (IOException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}

	private static void handleAuthRequest(HttpExchange exchange)
			throws IOException {
		UPSTREAM_REQUESTS.incrementAndGet();
		LAST_UPSTREAM_HEADERS.set(exchange.getRequestHeaders());
		LAST_UPSTREAM_PATH.set(exchange.getRequestURI().getPath());
		UPSTREAM_COOKIES.add(
				java.util.Optional.ofNullable(
								exchange.getRequestHeaders()
										.getFirst(HttpHeaders.COOKIE))
						.orElse("<none>"));
		exchange.getResponseHeaders().set(
				GatewayRequestContextFilter.REQUEST_ID_HEADER,
				exchange.getRequestHeaders().getFirst(
						GatewayRequestContextFilter.REQUEST_ID_HEADER));
		if ("/api/v1/auth/csrf".equals(exchange.getRequestURI().getPath())) {
			byte[] body = "{\"token\":\"synthetic-csrf\"}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set(
					HttpHeaders.CONTENT_TYPE,
					"application/json");
			exchange.getResponseHeaders().add(
					HttpHeaders.SET_COOKIE,
					"XSRF-TOKEN=synthetic-csrf; Path=/");
			exchange.sendResponseHeaders(200, body.length);
			try (var output = exchange.getResponseBody()) {
				output.write(body);
			}
			return;
		}
		exchange.sendResponseHeaders(204, -1);
		exchange.close();
	}

	private static void handleOrganisationRequest(HttpExchange exchange)
			throws IOException {
		UPSTREAM_REQUESTS.incrementAndGet();
		LAST_UPSTREAM_HEADERS.set(exchange.getRequestHeaders());
		LAST_UPSTREAM_PATH.set(exchange.getRequestURI().getPath());
		UPSTREAM_COOKIES.add(
				java.util.Optional.ofNullable(
								exchange.getRequestHeaders()
										.getFirst(HttpHeaders.COOKIE))
						.orElse("<none>"));
		byte[] body = """
				{"items":[],"totalElements":0}
				""".strip().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set(
				HttpHeaders.CONTENT_TYPE,
				"application/json");
		exchange.sendResponseHeaders(200, body.length);
		try (var output = exchange.getResponseBody()) {
			output.write(body);
		}
	}
}
