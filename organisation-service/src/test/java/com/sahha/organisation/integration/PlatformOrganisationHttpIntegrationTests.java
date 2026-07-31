package com.sahha.organisation.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
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

	@MockitoBean
	private JwtDecoder jwtDecoder;

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
}
