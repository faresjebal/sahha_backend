package com.sahha.scheduling.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
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

import com.sahha.scheduling.client.organisation.OrganisationContextClient;
import com.sahha.scheduling.client.organisation.OrganisationContextResource;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DoctorAvailabilityHttpIntegrationTests {

	private static final String DOCTOR_TOKEN = "schedule.doctor.token";
	private static final String RECEPTIONIST_TOKEN = "schedule.reception.token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private OrganisationContextClient organisationContextClient;

	@Test
	void doctorPublishesAvailabilityAndSlotsSubtractBreaksAndTimeOff()
			throws Exception {
		UUID organisationId = UUID.randomUUID();
		Workspace doctor = workspace(
				DOCTOR_TOKEN, organisationId, "DOCTOR");
		LocalDate monday = nextMonday();

		mockMvc.perform(put("/api/v1/availability/me")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", csrf().getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(scheduleBody(monday, "null")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.organisationId")
						.value(organisationId.toString()))
				.andExpect(jsonPath("$.doctorUserId")
						.value(doctor.userId().toString()))
				.andExpect(jsonPath("$.weeklyWindows.length()").value(1))
				.andExpect(jsonPath("$.version").value(0));

		mockMvc.perform(get("/api/v1/availability/me")
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.locationLabel")
						.value("Synthetic Tunis clinic"));

		mockMvc.perform(get(
					"/api/v1/availability/doctors/{doctorUserId}/slots",
					doctor.userId())
					.cookie(access(DOCTOR_TOKEN))
					.param("from", monday.toString())
					.param("to", monday.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slots.length()").value(5))
				.andExpect(jsonPath("$.slots[0].localStartTime").value("09:30:00"))
				.andExpect(jsonPath("$.slots[1].localStartTime").value("10:00:00"));
	}

	@Test
	void receptionistCanReadPublishedDoctorSlotsButCannotEditAvailability()
			throws Exception {
		UUID organisationId = UUID.randomUUID();
		Workspace doctor = workspace(DOCTOR_TOKEN, organisationId, "DOCTOR");
		LocalDate monday = nextMonday();
		mockMvc.perform(put("/api/v1/availability/me")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", csrf().getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(scheduleBody(monday, "null")))
				.andExpect(status().isOk());

		workspace(RECEPTIONIST_TOKEN, organisationId, "RECEPTIONIST");
		mockMvc.perform(get("/api/v1/availability/doctors")
					.cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].doctorUserId")
						.value(doctor.userId().toString()));

		mockMvc.perform(get(
					"/api/v1/availability/doctors/{doctorUserId}/slots",
					doctor.userId())
					.cookie(access(RECEPTIONIST_TOKEN))
					.param("from", monday.toString())
					.param("to", monday.toString()))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/availability/me")
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", csrf().getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(scheduleBody(monday, "null")))
				.andExpect(status().isForbidden());
	}

	@Test
	void unsafeUpdateRequiresCsrfAndCurrentVersion() throws Exception {
		UUID organisationId = UUID.randomUUID();
		workspace(DOCTOR_TOKEN, organisationId, "DOCTOR");
		LocalDate monday = nextMonday();

		mockMvc.perform(put("/api/v1/availability/me")
					.cookie(access(DOCTOR_TOKEN))
					.contentType(MediaType.APPLICATION_JSON)
					.content(scheduleBody(monday, "null")))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/availability/me")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", csrf().getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(scheduleBody(monday, "null")))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/availability/me")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", csrf().getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(scheduleBody(monday, "99")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:concurrent-availability-modification"));
	}

	private Workspace workspace(
			String token,
			UUID organisationId,
			String role) {
		UUID userId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token, userId, organisationId, List.of(role)));
		when(organisationContextClient.resolve(organisationId, token))
				.thenReturn(new OrganisationContextResource(
						membershipId,
						organisationId,
						"Synthetic clinic",
						"CLINIC",
						Set.of(role),
						0));
		return new Workspace(userId, membershipId);
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

	private static LocalDate nextMonday() {
		return LocalDate.now(ZoneOffset.UTC)
				.plusDays(1)
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", "scheduling-csrf-token");
	}

	private static String scheduleBody(LocalDate timeOffDate, String version) {
		return """
				{
				  "timeZone":"UTC",
				  "appointmentDurationMinutes":30,
				  "minimumLeadTimeMinutes":0,
				  "bookingHorizonDays":60,
				  "locationLabel":"Synthetic Tunis clinic",
				  "weeklyWindows":[{
				    "dayOfWeek":"MONDAY",
				    "startTime":"09:00",
				    "endTime":"13:00"
				  }],
				  "breaks":[{
				    "dayOfWeek":"MONDAY",
				    "startTime":"11:00",
				    "endTime":"12:00",
				    "label":"Lunch"
				  }],
				  "timeOff":[{
				    "date":"%s",
				    "startTime":"09:00",
				    "endTime":"09:30",
				    "reason":"Synthetic meeting"
				  }],
				  "version":%s
				}
				""".formatted(timeOffDate, version);
	}

	private record Workspace(UUID userId, UUID membershipId) {
	}
}
