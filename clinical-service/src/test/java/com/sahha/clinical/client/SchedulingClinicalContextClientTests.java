package com.sahha.clinical.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sahha.clinical.client.scheduling.ClinicalAppointmentContextResource;
import com.sahha.clinical.client.scheduling.ClinicalPatientAccessContextResource;
import com.sahha.clinical.client.scheduling.ClinicalAppointmentCompletionResource;
import com.sahha.clinical.client.scheduling.ClinicalCompletionRecoveryCommand;
import com.sahha.clinical.client.scheduling.SchedulingClinicalContextClient;
import com.sahha.clinical.config.ClinicalSecurityProperties;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.exception.SchedulingContextUnavailableException;

class SchedulingClinicalContextClientTests {

	private MockRestServiceServer server;
	private SchedulingClinicalContextClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl("http://scheduling-service");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new SchedulingClinicalContextClient(
				builder.build(),
				new ClinicalSecurityProperties(
						"SAHHA_ACCESS_TOKEN",
						URI.create("http://localhost:8081/.well-known/jwks.json"),
						"http://localhost:8081", "sahha-api",
						"XSRF-TOKEN", "X-XSRF-TOKEN", false));
	}

	@Test
	void forwardsOnlyAccessCookieAndReadsMinimalContext() {
		UUID appointmentId = UUID.randomUUID();
		server.expect(requestTo(
				"http://scheduling-service/api/v1/internal/clinical/appointments/"
						+ appointmentId))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.COOKIE,
						"SAHHA_ACCESS_TOKEN=aaa.bbb.ccc"))
				.andRespond(withSuccess(json(appointmentId), MediaType.APPLICATION_JSON));

		ClinicalAppointmentContextResource result =
				client.resolve(appointmentId, "aaa.bbb.ccc");

		assertEquals(appointmentId, result.appointmentId());
		assertEquals("IN_PROGRESS", result.status());
		server.verify();
	}

	@Test
	void mapsDeniedAndUnavailableWithoutTrustingPartialResponses() {
		UUID deniedId = UUID.randomUUID();
		UUID unavailableId = UUID.randomUUID();
		server.expect(requestTo(
				"http://scheduling-service/api/v1/internal/clinical/appointments/" + deniedId))
				.andRespond(withForbiddenRequest());
		server.expect(requestTo(
				"http://scheduling-service/api/v1/internal/clinical/appointments/"
						+ unavailableId))
				.andRespond(withServerError());

		assertThrows(ClinicalAccessDeniedException.class,
				() -> client.resolve(deniedId, "aaa.bbb.ccc"));
		assertThrows(SchedulingContextUnavailableException.class,
				() -> client.resolve(unavailableId, "aaa.bbb.ccc"));
		server.verify();
	}

	@Test
	void resolvesOnlyAnActiveMinimumPatientCareContext() {
		UUID patientRegistrationId = UUID.randomUUID();
		server.expect(requestTo(
				"http://scheduling-service/api/v1/internal/clinical/patients/"
						+ patientRegistrationId + "/access-context"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.COOKIE,
						"SAHHA_ACCESS_TOKEN=aaa.bbb.ccc"))
				.andRespond(withSuccess(patientAccessJson(patientRegistrationId),
						MediaType.APPLICATION_JSON));

		ClinicalPatientAccessContextResource result =
				client.resolvePatientAccess(
						patientRegistrationId, "aaa.bbb.ccc");

		assertEquals(patientRegistrationId, result.patientRegistrationId());
		assertEquals("CONFIRMED", result.status());
		server.verify();
	}

	@Test
	void recoveryForwardsTheAlreadyVerifiedDoubleSubmitCsrfContext() {
		UUID appointmentId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		UUID consultationId = UUID.randomUUID();
		Instant occurredAt = Instant.parse("2026-08-24T21:00:00Z");
		ClinicalCompletionRecoveryCommand command =
				new ClinicalCompletionRecoveryCommand(
						eventId, consultationId, 2, occurredAt);
		server.expect(requestTo(
				"http://scheduling-service/api/v1/internal/clinical/appointments/"
						+ appointmentId + "/completion-recovery"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.COOKIE,
						"SAHHA_ACCESS_TOKEN=aaa.bbb.ccc; XSRF-TOKEN=csrf-value"))
				.andExpect(header("X-XSRF-TOKEN", "csrf-value"))
				.andExpect(content().json("""
						{
						  "eventId":"%s",
						  "consultationId":"%s",
						  "clinicalResourceVersion":2,
						  "occurredAt":"%s"
						}
						""".formatted(eventId, consultationId, occurredAt)))
				.andRespond(withSuccess("""
						{
						  "eventId":"%s",
						  "appointmentId":"%s",
						  "outcome":"APPLIED",
						  "duplicate":false,
						  "appointmentStatus":"COMPLETED",
						  "appointmentVersion":4,
						  "conflictCode":null
						}
						""".formatted(eventId, appointmentId),
						MediaType.APPLICATION_JSON));

		ClinicalAppointmentCompletionResource result = client.recoverCompletion(
				appointmentId, command, "aaa.bbb.ccc", "csrf-value");

		assertEquals("APPLIED", result.outcome());
		assertEquals("COMPLETED", result.appointmentStatus());
		server.verify();
	}

	private static String json(UUID appointmentId) {
		return """
				{
				  "appointmentId":"%s",
				  "organisationId":"%s",
				  "patientRegistrationId":"%s",
				  "patientId":"%s",
				  "doctorUserId":"%s",
				  "doctorMembershipId":"%s",
				  "status":"IN_PROGRESS",
				  "startsAt":"2026-08-24T18:00:00Z",
				  "endsAt":"2026-08-24T18:30:00Z",
				  "version":3
				}
				""".formatted(
				appointmentId, UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
	}

	private static String patientAccessJson(UUID patientRegistrationId) {
		return """
				{
				  "appointmentId":"%s",
				  "organisationId":"%s",
				  "patientRegistrationId":"%s",
				  "patientId":"%s",
				  "doctorUserId":"%s",
				  "doctorMembershipId":"%s",
				  "status":"CONFIRMED",
				  "appointmentVersion":2
				}
				""".formatted(
				UUID.randomUUID(), UUID.randomUUID(), patientRegistrationId,
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
	}
}
