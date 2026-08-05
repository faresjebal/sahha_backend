package com.sahha.patient.integration;

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
import java.util.Set;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.sahha.patient.client.organisation.OrganisationContextClient;
import com.sahha.patient.client.organisation.OrganisationContextResource;
import com.sahha.patient.repository.PatientAuditEventRepository;
import com.sahha.patient.repository.PatientIdentityRepository;
import com.sahha.patient.repository.PatientOrganisationRegistrationRepository;
import com.sahha.patient.repository.PatientOutboxEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PatientAdministrativeHttpIntegrationTests {

	private static final String RECEPTIONIST_TOKEN = "patient.reception.token";
	private static final String OTHER_TOKEN = "patient.other.token";
	private static final String DOCTOR_TOKEN = "patient.doctor.token";
	private static final String RAW_IDENTIFIER = "TN-0735-8821";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PatientIdentityRepository identityRepository;

	@Autowired
	private PatientOrganisationRegistrationRepository registrationRepository;

	@Autowired
	private PatientAuditEventRepository auditRepository;

	@Autowired
	private PatientOutboxEventRepository outboxRepository;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private OrganisationContextClient organisationContextClient;

	@Test
	void receptionistRegistersSearchesAndReadsOnlyAdministrativeProjection()
			throws Exception {
		Workspace workspace = workspace(
				RECEPTIONIST_TOKEN,
				"RECEPTIONIST");
		Cookie csrf = csrf();
		String createdBody = mockMvc.perform(post("/api/v1/patients")
					.cookie(access(RECEPTIONIST_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.header("X-Request-ID", "patient-create-request")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("Amina", "Ben Salem", RAW_IDENTIFIER, "")))
				.andExpect(status().isCreated())
				.andExpect(header().string(
						HttpHeaders.LOCATION,
						containsString("/api/v1/patients/")))
				.andExpect(jsonPath("$.organisationId")
						.value(workspace.organisationId().toString()))
				.andExpect(jsonPath("$.medicalRecordNumber")
						.value(org.hamcrest.Matchers.matchesPattern(
								"PT-[0-9A-F]{12}")))
				.andExpect(jsonPath("$.maskedIdentifier").value("****8821"))
				.andExpect(jsonPath("$.registrationVersion").value(0))
				.andExpect(jsonPath("$.identityVersion").value(0))
				.andReturn().getResponse().getContentAsString();
		JsonNode created = JSON.readTree(createdBody);
		String registrationId = created.get("registrationId").asText();

		assertFalse(createdBody.contains(RAW_IDENTIFIER));
		assertAdministrativeOnly(createdBody);

		String pageBody = mockMvc.perform(get("/api/v1/patients")
					.cookie(access(RECEPTIONIST_TOKEN))
					.param("query", "Amina"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].registrationId")
						.value(registrationId))
				.andExpect(jsonPath("$.items[0].firstName").value("Amina"))
				.andReturn().getResponse().getContentAsString();
		assertAdministrativeOnly(pageBody);

		String detailBody = mockMvc.perform(get(
					"/api/v1/patients/{registrationId}",
					registrationId)
					.cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("amina@example.test"))
				.andReturn().getResponse().getContentAsString();
		assertAdministrativeOnly(detailBody);
		assertEquals(1, identityRepository.count());
		assertEquals(1, registrationRepository.count());
		assertEquals(1, outboxRepository.count());
		assertEquals(3, auditRepository.countByOrganisationId(
				workspace.organisationId()));
		outboxRepository.findAll().forEach(event -> {
			assertFalse(event.getPayload().containsKey("firstName"));
			assertFalse(event.getPayload().containsKey("phoneNumber"));
			assertFalse(event.getPayload().containsKey("identifier"));
		});
	}

	@Test
	void exactIdentifierRequiresReviewAndLinksOnlyAfterExplicitDecision()
			throws Exception {
		Workspace first = workspace(RECEPTIONIST_TOKEN, "RECEPTIONIST");
		Workspace second = workspace(OTHER_TOKEN, "ORGANIZATION_ADMIN");
		Cookie csrf = csrf();
		String firstBody = mockMvc.perform(post("/api/v1/patients")
					.cookie(access(RECEPTIONIST_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("Amina", "Ben Salem", RAW_IDENTIFIER, "")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode firstRegistration = JSON.readTree(firstBody);
		String patientId = firstRegistration.get("patientId").asText();
		String firstRegistrationId = firstRegistration.get("registrationId")
				.asText();

		String duplicateBody = mockMvc.perform(post(
					"/api/v1/patients/duplicate-check")
					.cookie(access(OTHER_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(duplicateCheckBody(RAW_IDENTIFIER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewRequired").value(true))
				.andExpect(jsonPath("$.exactStrongIdentifierMatch").value(true))
				.andExpect(jsonPath("$.candidates[0].patientId").value(patientId))
				.andExpect(jsonPath(
						"$.candidates[0].registeredInActiveOrganisation")
						.value(false))
				.andReturn().getResponse().getContentAsString();
		assertFalse(duplicateBody.contains(RAW_IDENTIFIER));

		mockMvc.perform(post("/api/v1/patients")
					.cookie(access(OTHER_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("Amina", "Ben Salem", RAW_IDENTIFIER, "")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:patient-duplicate-review-required"));

		String decision = """
				,"duplicateDecision":"LINK_EXISTING"
				,"selectedPatientId":"%s"
				,"duplicateDecisionReason":"SAME_PERSON_CONFIRMED"
				""".formatted(patientId);
		mockMvc.perform(post("/api/v1/patients")
					.cookie(access(OTHER_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody(
							"Amina",
							"Ben Salem",
							RAW_IDENTIFIER,
							decision)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.patientId").value(patientId))
				.andExpect(jsonPath("$.organisationId")
						.value(second.organisationId().toString()));

		mockMvc.perform(get(
					"/api/v1/patients/{registrationId}",
					firstRegistrationId)
					.cookie(access(OTHER_TOKEN)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:patient-registration-not-found"));
		assertEquals(1, identityRepository.count());
		assertEquals(2, registrationRepository.count());
		assertEquals(first.organisationId(),
				UUID.fromString(firstRegistration.get("organisationId").asText()));
	}

	@Test
	void possibleContactDuplicateCanBeOverriddenOnlyWithReviewedReason()
			throws Exception {
		workspace(RECEPTIONIST_TOKEN, "RECEPTIONIST");
		Cookie csrf = csrf();
		mockMvc.perform(post("/api/v1/patients")
					.cookie(access(RECEPTIONIST_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("Amina", "Ben Salem", null, "")))
				.andExpect(status().isCreated());

		String possible = createBody(
				"Sami",
				"Trabelsi",
				null,
				"").replace(
				"amina@example.test",
				"sami@example.test");
		mockMvc.perform(post("/api/v1/patients")
					.cookie(access(RECEPTIONIST_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(possible))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.duplicateCheck.candidates[0].matchReasons[0]")
						.value("PHONE_NUMBER"));

		String decision = """
				,"duplicateDecision":"CREATE_NEW"
				,"duplicateDecisionReason":"CONTACT_INFORMATION_SHARED"
				""";
		mockMvc.perform(post("/api/v1/patients")
					.cookie(access(RECEPTIONIST_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody(
							"Sami",
							"Trabelsi",
							null,
							decision).replace(
							"amina@example.test",
							"sami@example.test")))
				.andExpect(status().isCreated());
		assertEquals(2, identityRepository.count());
	}

	@Test
	void staleVersionMissingCsrfAndDoctorRoleAreDenied() throws Exception {
		workspace(RECEPTIONIST_TOKEN, "RECEPTIONIST");
		workspace(DOCTOR_TOKEN, "DOCTOR");
		Cookie csrf = csrf();
		String createdBody = mockMvc.perform(post("/api/v1/patients")
					.cookie(access(RECEPTIONIST_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("Amina", "Ben Salem", null, "")))
				.andReturn().getResponse().getContentAsString();
		String registrationId = JSON.readTree(createdBody)
				.get("registrationId").asText();

		mockMvc.perform(post("/api/v1/patients")
					.cookie(access(RECEPTIONIST_TOKEN))
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("No", "Csrf", null, "")))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/patients")
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isForbidden());

		mockMvc.perform(put(
					"/api/v1/patients/{registrationId}",
					registrationId)
					.cookie(access(RECEPTIONIST_TOKEN), csrf)
					.header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody(99, 0)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:concurrent-patient-modification"));
	}

	private Workspace workspace(String token, String role) {
		UUID organisationId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token,
				userId,
				organisationId,
				List.of(role)));
		when(organisationContextClient.resolve(organisationId, token))
				.thenReturn(new OrganisationContextResource(
						UUID.randomUUID(),
						organisationId,
						"Synthetic clinic",
						"CLINIC",
						Set.of(role),
						0));
		return new Workspace(organisationId, userId);
	}

	private static Jwt jwt(
			String token,
			UUID userId,
			UUID organisationId,
			List<String> organisationRoles) {
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
				.claim("org_roles", organisationRoles)
				.claim("token_type", "access")
				.build();
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", "patient-csrf-token");
	}

	private static String createBody(
			String firstName,
			String lastName,
			String identifier,
			String decisionFields) {
		String identifierField = identifier == null
				? ""
				: """
						,"identifier":{
						  "type":"NATIONAL_ID",
						  "value":"%s",
						  "countryCode":"TN"
						}
						""".formatted(identifier);
		return """
				{
				  "firstName":"%s",
				  "lastName":"%s",
				  "dateOfBirth":"1990-04-12",
				  "sex":"FEMALE"
				  %s,
				  "phoneNumber":"+216 20 123 456",
				  "email":"amina@example.test",
				  "address":"12 Synthetic Street",
				  "city":"Tunis",
				  "region":"Tunis",
				  "postalCode":"1000",
				  "countryCode":"TN",
				  "emergencyContactName":"Synthetic Contact",
				  "emergencyContactPhone":"+216 70 000 000",
				  "emergencyContactRelationship":"Sibling",
				  "preferredLanguage":"Arabic",
				  "accessibilityNeeds":"Wheelchair access",
				  "privacyNoticeAcknowledged":true
				  %s
				}
				""".formatted(firstName, lastName, identifierField, decisionFields);
	}

	private static String duplicateCheckBody(String identifier) {
		return """
				{
				  "firstName":"Amina",
				  "lastName":"Ben Salem",
				  "dateOfBirth":"1990-04-12",
				  "sex":"FEMALE",
				  "phoneNumber":"+216 20 123 456",
				  "email":"amina@example.test",
				  "identifier":{
				    "type":"NATIONAL_ID",
				    "value":"%s",
				    "countryCode":"TN"
				  }
				}
				""".formatted(identifier);
	}

	private static String updateBody(long registrationVersion, long identityVersion) {
		return """
				{
				  "firstName":"Amina",
				  "lastName":"Ben Salem",
				  "dateOfBirth":"1990-04-12",
				  "sex":"FEMALE",
				  "removeIdentifier":false,
				  "phoneNumber":"+216 20 123 456",
				  "email":"amina@example.test",
				  "address":"12 Synthetic Street",
				  "city":"Tunis",
				  "region":"Tunis",
				  "postalCode":"1000",
				  "countryCode":"TN",
				  "emergencyContactName":"Synthetic Contact",
				  "emergencyContactPhone":"+216 70 000 000",
				  "emergencyContactRelationship":"Sibling",
				  "preferredLanguage":"Arabic",
				  "accessibilityNeeds":"Wheelchair access",
				  "registrationVersion":%d,
				  "identityVersion":%d
				}
				""".formatted(registrationVersion, identityVersion);
	}

	private static void assertAdministrativeOnly(String json) {
		for (String prohibited : List.of(
				"diagnosis",
				"clinicalNotes",
				"allergies",
				"prescriptions",
				"medications",
				"vitals")) {
			assertFalse(json.contains(prohibited));
		}
	}

	private record Workspace(UUID organisationId, UUID userId) {
	}
}
