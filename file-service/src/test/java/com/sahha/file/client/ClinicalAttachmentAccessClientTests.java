package com.sahha.file.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sahha.file.client.clinical.ClinicalAttachmentAccessClient;
import com.sahha.file.client.clinical.ClinicalAttachmentContextResource;
import com.sahha.file.config.FileSecurityProperties;
import com.sahha.file.exception.ClinicalContextUnavailableException;
import com.sahha.file.exception.FileAccessDeniedException;
import com.sahha.file.exception.FileResourceNotFoundException;

class ClinicalAttachmentAccessClientTests {

	private MockRestServiceServer server;
	private ClinicalAttachmentAccessClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl("http://clinical-service");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new ClinicalAttachmentAccessClient(
				builder.build(),
				new FileSecurityProperties(
						"SAHHA_ACCESS_TOKEN",
						URI.create("http://localhost:8081/.well-known/jwks.json"),
						"http://localhost:8081", "sahha-api",
						"XSRF-TOKEN", "X-XSRF-TOKEN", false));
	}

	@Test
	void forwardsOnlyTheAccessCookieAndAcceptsAnExactMinimumContext() {
		Fixture fixture = fixture();
		server.expect(requestTo(url(fixture.consultationId())))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.COOKIE,
						"SAHHA_ACCESS_TOKEN=aaa.bbb.ccc"))
				.andRespond(withSuccess(fixture.json(), MediaType.APPLICATION_JSON));

		ClinicalAttachmentContextResource result = client.resolve(
				fixture.consultationId(), fixture.organisationId(),
				fixture.doctorUserId(), "aaa.bbb.ccc");

		assertEquals(fixture.patientId(), result.patientId());
		assertEquals("DRAFT", result.consultationStatus());
		server.verify();
	}

	@Test
	void rejectsMismatchedIdentityEvenWhenTheDependencyReturnsSuccess() {
		Fixture fixture = fixture();
		server.expect(requestTo(url(fixture.consultationId())))
				.andRespond(withSuccess(
						fixture.jsonWithDoctor(UUID.randomUUID()),
						MediaType.APPLICATION_JSON));

		assertThrows(ClinicalContextUnavailableException.class,
				() -> client.resolve(
						fixture.consultationId(), fixture.organisationId(),
						fixture.doctorUserId(), "aaa.bbb.ccc"));
		server.verify();
	}

	@Test
	void mapsHiddenDeniedAndUnavailableDependenciesSafely() {
		Fixture hidden = fixture();
		Fixture denied = fixture();
		Fixture unavailable = fixture();
		server.expect(requestTo(url(hidden.consultationId())))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(url(denied.consultationId())))
				.andRespond(withForbiddenRequest());
		server.expect(requestTo(url(unavailable.consultationId())))
				.andRespond(withServerError());

		assertThrows(FileResourceNotFoundException.class,
				() -> resolve(hidden));
		assertThrows(FileAccessDeniedException.class,
				() -> resolve(denied));
		assertThrows(ClinicalContextUnavailableException.class,
				() -> resolve(unavailable));
		server.verify();
	}

	private ClinicalAttachmentContextResource resolve(Fixture fixture) {
		return client.resolve(
				fixture.consultationId(), fixture.organisationId(),
				fixture.doctorUserId(), "aaa.bbb.ccc");
	}

	private static String url(UUID consultationId) {
		return "http://clinical-service/api/v1/internal/clinical/consultations/"
				+ consultationId + "/attachment-context";
	}

	private static Fixture fixture() {
		return new Fixture(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID());
	}

	private record Fixture(
			UUID consultationId,
			UUID organisationId,
			UUID patientRegistrationId,
			UUID patientId,
			UUID doctorUserId) {

		String json() {
			return jsonWithDoctor(doctorUserId);
		}

		String jsonWithDoctor(UUID returnedDoctorId) {
			return """
					{
					  "consultationId":"%s",
					  "organisationId":"%s",
					  "patientRegistrationId":"%s",
					  "patientId":"%s",
					  "doctorUserId":"%s",
					  "consultationStatus":"DRAFT",
					  "consultationVersion":1
					}
					""".formatted(
					consultationId, organisationId, patientRegistrationId,
					patientId, returnedDoctorId);
		}
	}
}
