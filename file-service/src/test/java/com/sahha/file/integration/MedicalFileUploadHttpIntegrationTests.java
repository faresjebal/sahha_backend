package com.sahha.file.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.sahha.file.client.clinical.ClinicalAttachmentAccessClient;
import com.sahha.file.client.clinical.ClinicalAttachmentContextResource;
import com.sahha.file.entity.FileUploadStatus;
import com.sahha.file.entity.FileScanStatus;
import com.sahha.file.entity.FileOutboxEvent;
import com.sahha.file.entity.MedicalFile;
import com.sahha.file.exception.FileResourceNotFoundException;
import com.sahha.file.repository.FileAuditEventRepository;
import com.sahha.file.repository.FileDownloadGrantRepository;
import com.sahha.file.repository.FileOutboxEventRepository;
import com.sahha.file.repository.MedicalFileRepository;
import com.sahha.file.storage.PrivateObjectStorage;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MedicalFileUploadHttpIntegrationTests.MutableClockConfiguration.class)
class MedicalFileUploadHttpIntegrationTests {

	private static final String DOCTOR_TOKEN = "aaa.bbb.ccc";
	private static final String RECEPTIONIST_TOKEN = "ddd.eee.fff";
	private static final String OTHER_DOCTOR_TOKEN = "ggg.hhh.iii";
	private static final String CSRF_VALUE = "file-csrf-token";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired private MockMvc mockMvc;
	@Autowired private MedicalFileRepository fileRepository;
	@Autowired private FileAuditEventRepository auditRepository;
	@Autowired private FileDownloadGrantRepository grantRepository;
	@Autowired private FileOutboxEventRepository outboxRepository;
	@Autowired private PrivateObjectStorage objectStorage;
	@Autowired private MutableClock mutableClock;

	@MockitoBean private JwtDecoder jwtDecoder;
	@MockitoBean private ClinicalAttachmentAccessClient clinicalClient;

	@BeforeEach
	void resetClock() {
		mutableClock.set(Instant.parse("2026-08-30T00:00:00Z"));
	}

	@Test
	void doctorNegotiatesAndStreamsPrivateBytesWithAOneTimeTicket()
			throws Exception {
		Fixture fixture = fixture();
		byte[] bytes = "synthetic-medical-pdf".getBytes(StandardCharsets.UTF_8);
		doctorWorkspace(DOCTOR_TOKEN, fixture);
		when(clinicalClient.resolve(
				fixture.consultationId(), fixture.organisationId(),
				fixture.doctorUserId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());

		JsonNode ticket = negotiate(fixture, bytes.length, checksum(bytes))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.uploadStatus").value("NEGOTIATED"))
				.andExpect(jsonPath("$.scanStatus").value("PENDING"))
				.andExpect(jsonPath("$.storageKey").doesNotExist())
				.andExpect(jsonPath("$.patientId").doesNotExist())
				.andReturnBody();
		UUID fileId = UUID.fromString(ticket.get("fileId").asText());
		String uploadToken = ticket.get("uploadToken").asText();
		assertNotEquals(uploadToken,
				fileRepository.findById(fileId).orElseThrow().getUploadTicketDigest());

		mockMvc.perform(put("/api/v1/files/{fileId}/content", fileId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.header("X-Upload-Token", uploadToken)
					.header(HttpHeaders.CONTENT_LENGTH, bytes.length)
					.contentType(MediaType.APPLICATION_PDF)
					.content(bytes))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.fileId").value(fileId.toString()))
				.andExpect(jsonPath("$.uploadStatus").value("STORED"))
				.andExpect(jsonPath("$.scanStatus").value("PENDING"))
				.andExpect(jsonPath("$.checksumSha256").value(checksum(bytes)));

		MedicalFile stored = fileRepository.findById(fileId).orElseThrow();
		assertEquals(FileUploadStatus.STORED, stored.getUploadStatus());
		assertEquals(2, auditRepository.countByMedicalFileId(fileId));
		assertEquals(bytes.length, stored.getActualSize());
		assertFalse(stored.getStorageKey().contains("report.pdf"));
		org.junit.jupiter.api.Assertions.assertTrue(
				objectStorage.exists(stored.getStorageKey()));

		mockMvc.perform(put("/api/v1/files/{fileId}/content", fileId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.header("X-Upload-Token", uploadToken)
					.header(HttpHeaders.CONTENT_LENGTH, bytes.length)
					.contentType(MediaType.APPLICATION_PDF)
					.content(bytes))
				.andExpect(status().isConflict());
	}

	@Test
	void receptionistAndMissingCsrfNeverReachClinicalEligibility()
			throws Exception {
		Fixture fixture = fixture();
		workspace(RECEPTIONIST_TOKEN, fixture, List.of("RECEPTIONIST"));
		doctorWorkspace(DOCTOR_TOKEN, fixture);

		mockMvc.perform(post("/api/v1/files/uploads")
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(declaration(fixture.consultationId(), 10, null)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/files/uploads")
					.cookie(access(DOCTOR_TOKEN))
					.contentType(MediaType.APPLICATION_JSON)
					.content(declaration(fixture.consultationId(), 10, null)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/files/{fileId}/synthetic-scan",
					UUID.randomUUID())
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"decision\":\"CLEAN\"}"))
				.andExpect(status().isForbidden());

		verifyNoInteractions(clinicalClient);
	}

	@Test
	void hiddenConsultationAndUnsafeDeclarationsFailClosed()
			throws Exception {
		Fixture fixture = fixture();
		doctorWorkspace(DOCTOR_TOKEN, fixture);
		when(clinicalClient.resolve(
				fixture.consultationId(), fixture.organisationId(),
				fixture.doctorUserId(), DOCTOR_TOKEN))
				.thenThrow(new FileResourceNotFoundException());

		negotiate(fixture, 10, null)
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/files/uploads")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "consultationId":"%s",
							  "originalFilename":"malware.exe",
							  "contentType":"application/octet-stream",
							  "declaredSize":10
							}
							""".formatted(fixture.consultationId())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void checksumMismatchMarksUploadFailedAndRemovesPrivateBytes()
			throws Exception {
		Fixture fixture = fixture();
		byte[] bytes = "synthetic-image".getBytes(StandardCharsets.UTF_8);
		doctorWorkspace(DOCTOR_TOKEN, fixture);
		when(clinicalClient.resolve(any(), eq(fixture.organisationId()),
				eq(fixture.doctorUserId()), eq(DOCTOR_TOKEN)))
				.thenReturn(fixture.context());
		JsonNode ticket = negotiate(fixture, bytes.length, "0".repeat(64))
				.andExpect(status().isCreated())
				.andReturnBody();
		UUID fileId = UUID.fromString(ticket.get("fileId").asText());

		mockMvc.perform(put("/api/v1/files/{fileId}/content", fileId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.header("X-Upload-Token", ticket.get("uploadToken").asText())
					.header(HttpHeaders.CONTENT_LENGTH, bytes.length)
					.contentType(MediaType.APPLICATION_PDF)
					.content(bytes))
				.andExpect(status().isBadRequest());

		MedicalFile failed = fileRepository.findById(fileId).orElseThrow();
		assertEquals(FileUploadStatus.FAILED, failed.getUploadStatus());
		assertEquals(2, auditRepository.countByMedicalFileId(fileId));
		assertFalse(objectStorage.exists(failed.getStorageKey()));
	}

	@Test
	void explicitSyntheticCleanIsIdempotentAndCreatesMinimalOutbox()
			throws Exception {
		Fixture fixture = fixture();
		StoredUpload upload = storedUpload(
				fixture, "synthetic-clean-pdf".getBytes(StandardCharsets.UTF_8));
		MedicalFile pending = fileRepository.findById(upload.fileId()).orElseThrow();
		assertFalse(pending.isAvailable());
		assertEquals(0, outboxRepository.countByMedicalFileId(upload.fileId()));

		mockMvc.perform(post("/api/v1/files/{fileId}/synthetic-scan", upload.fileId())
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.header("X-Request-ID", "synthetic-clean-test")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"decision\":\"CLEAN\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scanStatus").value("CLEAN"))
				.andExpect(jsonPath("$.availableAt").isNotEmpty())
				.andExpect(jsonPath("$.rejectedAt").doesNotExist())
				.andExpect(jsonPath("$.alreadyApplied").value(false));

		MedicalFile clean = fileRepository.findById(upload.fileId()).orElseThrow();
		assertTrue(clean.isAvailable());
		assertTrue(objectStorage.exists(clean.getStorageKey()));
		assertEquals(3, auditRepository.countByMedicalFileId(upload.fileId()));
		assertEquals(1, outboxRepository.countByMedicalFileId(upload.fileId()));
		FileOutboxEvent event = outboxRepository.findAll().stream()
				.filter(item -> item.getMedicalFileId().equals(upload.fileId()))
				.findFirst().orElseThrow();
		assertEquals("medical-file.available.v1", event.getEventType());
		assertFalse(event.getPayload().containsKey("filename"));
		assertFalse(event.getPayload().containsKey("checksum"));
		assertFalse(event.getPayload().containsKey("storageKey"));

		mockMvc.perform(post("/api/v1/files/{fileId}/synthetic-scan", upload.fileId())
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"decision\":\"CLEAN\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alreadyApplied").value(true));
		assertEquals(3, auditRepository.countByMedicalFileId(upload.fileId()));
		assertEquals(1, outboxRepository.countByMedicalFileId(upload.fileId()));
	}

	@Test
	void syntheticRejectionMakesFileUnavailableAndRemovesBytes()
			throws Exception {
		Fixture fixture = fixture();
		StoredUpload upload = storedUpload(
				fixture, "synthetic-reject-pdf".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(post("/api/v1/files/{fileId}/synthetic-scan", upload.fileId())
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.header("X-Request-ID", "synthetic-reject-test")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"decision\":\"REJECTED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scanStatus").value("REJECTED"))
				.andExpect(jsonPath("$.availableAt").doesNotExist())
				.andExpect(jsonPath("$.rejectedAt").isNotEmpty());

		MedicalFile rejected = fileRepository.findById(upload.fileId()).orElseThrow();
		assertEquals(FileScanStatus.REJECTED, rejected.getScanStatus());
		assertFalse(rejected.isAvailable());
		assertFalse(objectStorage.exists(rejected.getStorageKey()));
		assertEquals(3, auditRepository.countByMedicalFileId(upload.fileId()));
		assertEquals(1, outboxRepository.countByMedicalFileId(upload.fileId()));
		FileOutboxEvent event = outboxRepository.findAll().stream()
				.filter(item -> item.getMedicalFileId().equals(upload.fileId()))
				.findFirst().orElseThrow();
		assertEquals("medical-file.rejected.v1", event.getEventType());
	}

	@Test
	void scanDecisionBeforeStoredContentReturnsAConflict()
			throws Exception {
		Fixture fixture = fixture();
		doctorWorkspace(DOCTOR_TOKEN, fixture);
		when(clinicalClient.resolve(
				fixture.consultationId(), fixture.organisationId(),
				fixture.doctorUserId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());
		JsonNode ticket = negotiate(fixture, 12, null)
				.andExpect(status().isCreated())
				.andReturnBody();

		mockMvc.perform(post("/api/v1/files/{fileId}/synthetic-scan",
					ticket.get("fileId").asText())
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"decision\":\"CLEAN\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:file-scan-conflict"));
	}

	@Test
	void owningDoctorListsIssuesAndConsumesOneDownloadGrant() throws Exception {
		Fixture fixture = fixture();
		byte[] bytes = "clean-synthetic-download".getBytes(StandardCharsets.UTF_8);
		StoredUpload upload = storedUpload(fixture, bytes);
		markClean(upload.fileId());

		mockMvc.perform(get("/api/v1/files")
					.queryParam("consultationId", fixture.consultationId().toString())
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].fileId").value(upload.fileId().toString()))
				.andExpect(jsonPath("$[0].originalFilename").value("report.pdf"))
				.andExpect(jsonPath("$[0].downloadAvailable").value(true))
				.andExpect(jsonPath("$[0].storageKey").doesNotExist())
				.andExpect(jsonPath("$[0].checksumSha256").doesNotExist())
				.andExpect(jsonPath("$[0].patientId").doesNotExist());

		JsonNode grant = issueGrant(upload.fileId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.downloadPath")
						.value("/api/v1/files/%s/content".formatted(upload.fileId())))
				.andExpect(jsonPath("$.storageKey").doesNotExist())
				.andReturnBody();
		String downloadToken = grant.get("downloadToken").asText();
		assertFalse(downloadToken.isBlank());

		mockMvc.perform(get("/api/v1/files/{fileId}/content", upload.fileId())
					.cookie(access(DOCTOR_TOKEN))
					.header("X-Download-Token", downloadToken))
				.andExpect(status().isOk())
				.andExpect(content().bytes(bytes))
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, bytes.length))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						org.hamcrest.Matchers.containsString("report.pdf")));

		mockMvc.perform(get("/api/v1/files/{fileId}/content", upload.fileId())
					.cookie(access(DOCTOR_TOKEN))
					.header("X-Download-Token", downloadToken))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:file-download-conflict"));
		assertEquals(6, auditRepository.countByMedicalFileId(upload.fileId()));
		assertEquals(1, outboxRepository.countByMedicalFileId(upload.fileId()));
	}

	@Test
	void pendingFileCannotReceiveADownloadGrant() throws Exception {
		Fixture fixture = fixture();
		StoredUpload upload = storedUpload(
				fixture, "pending-download".getBytes(StandardCharsets.UTF_8));

		issueGrant(upload.fileId())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:file-download-conflict"));
		assertEquals(3, auditRepository.countByMedicalFileId(upload.fileId()));
	}

	@Test
	void expiredGrantIsDeniedAuditedAndNeverStreamsBytes() throws Exception {
		Fixture fixture = fixture();
		StoredUpload upload = storedUpload(
				fixture, "expired-download".getBytes(StandardCharsets.UTF_8));
		markClean(upload.fileId());
		JsonNode grant = issueGrant(upload.fileId())
				.andExpect(status().isOk())
				.andReturnBody();
		mutableClock.advance(Duration.ofMinutes(3));

		mockMvc.perform(get("/api/v1/files/{fileId}/content", upload.fileId())
					.cookie(access(DOCTOR_TOKEN))
					.header("X-Download-Token", grant.get("downloadToken").asText()))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:file-download-grant-expired"));
		assertEquals(5, auditRepository.countByMedicalFileId(upload.fileId()));
	}

	@Test
	void unrelatedDoctorReceptionistAndInvalidGrantCannotReadAFile()
			throws Exception {
		Fixture fixture = fixture();
		StoredUpload upload = storedUpload(
				fixture, "private-download".getBytes(StandardCharsets.UTF_8));
		markClean(upload.fileId());
		workspace(RECEPTIONIST_TOKEN, fixture, List.of("RECEPTIONIST"));
		UUID otherDoctorId = UUID.randomUUID();
		when(jwtDecoder.decode(OTHER_DOCTOR_TOKEN)).thenReturn(jwt(
				OTHER_DOCTOR_TOKEN, otherDoctorId, fixture.organisationId(),
				List.of("DOCTOR")));

		mockMvc.perform(post("/api/v1/files/{fileId}/download-grants", upload.fileId())
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/files/{fileId}/download-grants", upload.fileId())
					.cookie(access(OTHER_DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/files/{fileId}/content", upload.fileId())
					.cookie(access(DOCTOR_TOKEN))
					.header("X-Download-Token", "not-a-real-grant"))
				.andExpect(status().isNotFound());
		assertEquals(4, auditRepository.countByMedicalFileId(upload.fileId()));
	}

	@Test
	void revokedClinicalRelationshipPreventsAndAuditsGrantIssuance()
			throws Exception {
		Fixture fixture = fixture();
		StoredUpload upload = storedUpload(
				fixture, "revoked-access".getBytes(StandardCharsets.UTF_8));
		markClean(upload.fileId());
		when(clinicalClient.resolve(
				fixture.consultationId(), fixture.organisationId(),
				fixture.doctorUserId(), DOCTOR_TOKEN))
				.thenThrow(new FileResourceNotFoundException());

		issueGrant(upload.fileId())
				.andExpect(status().isNotFound());
		assertEquals(4, auditRepository.countByMedicalFileId(upload.fileId()));
		assertEquals(0, grantRepository.count());
	}

	@Test
	void missingPrivateObjectFailsClosedAndConsumesTheGrant() throws Exception {
		Fixture fixture = fixture();
		StoredUpload upload = storedUpload(
				fixture, "missing-object".getBytes(StandardCharsets.UTF_8));
		markClean(upload.fileId());
		JsonNode grant = issueGrant(upload.fileId())
				.andExpect(status().isOk())
				.andReturnBody();
		MedicalFile file = fileRepository.findById(upload.fileId()).orElseThrow();
		objectStorage.remove(file.getStorageKey());

		mockMvc.perform(get("/api/v1/files/{fileId}/content", upload.fileId())
					.cookie(access(DOCTOR_TOKEN))
					.header("X-Download-Token", grant.get("downloadToken").asText()))
				.andExpect(status().isServiceUnavailable());
		mockMvc.perform(get("/api/v1/files/{fileId}/content", upload.fileId())
					.cookie(access(DOCTOR_TOKEN))
					.header("X-Download-Token", grant.get("downloadToken").asText()))
				.andExpect(status().isConflict());
		assertEquals(7, auditRepository.countByMedicalFileId(upload.fileId()));
	}

	private StoredUpload storedUpload(Fixture fixture, byte[] bytes)
			throws Exception {
		doctorWorkspace(DOCTOR_TOKEN, fixture);
		when(clinicalClient.resolve(
				fixture.consultationId(), fixture.organisationId(),
				fixture.doctorUserId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());
		JsonNode ticket = negotiate(fixture, bytes.length, checksum(bytes))
				.andExpect(status().isCreated())
				.andReturnBody();
		UUID fileId = UUID.fromString(ticket.get("fileId").asText());
		mockMvc.perform(put("/api/v1/files/{fileId}/content", fileId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.header("X-Upload-Token", ticket.get("uploadToken").asText())
					.header(HttpHeaders.CONTENT_LENGTH, bytes.length)
					.contentType(MediaType.APPLICATION_PDF)
					.content(bytes))
				.andExpect(status().isAccepted());
		return new StoredUpload(fileId);
	}

	private void markClean(UUID fileId) throws Exception {
		mockMvc.perform(post("/api/v1/files/{fileId}/synthetic-scan", fileId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"decision\":\"CLEAN\"}"))
				.andExpect(status().isOk());
	}

	private Result issueGrant(UUID fileId) throws Exception {
		return new Result(mockMvc.perform(
				post("/api/v1/files/{fileId}/download-grants", fileId)
						.cookie(access(DOCTOR_TOKEN), csrf())
						.header("X-XSRF-TOKEN", CSRF_VALUE)
						.header("X-Request-ID", "download-grant-test")));
	}

	private Result negotiate(
			Fixture fixture,
			long declaredSize,
			String checksum) throws Exception {
		return new Result(mockMvc.perform(post("/api/v1/files/uploads")
				.cookie(access(DOCTOR_TOKEN), csrf())
				.header("X-XSRF-TOKEN", CSRF_VALUE)
				.header("X-Request-ID", "file-upload-test")
				.contentType(MediaType.APPLICATION_JSON)
				.content(declaration(
						fixture.consultationId(), declaredSize, checksum))));
	}

	private void doctorWorkspace(String token, Fixture fixture) {
		workspace(token, fixture, List.of("DOCTOR"));
	}

	private void workspace(String token, Fixture fixture, List<String> roles) {
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token, fixture.doctorUserId(), fixture.organisationId(), roles));
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

	private static String declaration(
			UUID consultationId,
			long declaredSize,
			String checksum) {
		String checksumField = checksum == null ? "" :
				",\"expectedChecksumSha256\":\"" + checksum + "\"";
		return """
				{
				  "consultationId":"%s",
				  "originalFilename":"report.pdf",
				  "contentType":"application/pdf",
				  "declaredSize":%d%s
				}
				""".formatted(consultationId, declaredSize, checksumField);
	}

	private static String checksum(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(bytes));
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

		ClinicalAttachmentContextResource context() {
			return new ClinicalAttachmentContextResource(
					consultationId, organisationId, patientRegistrationId,
					patientId, doctorUserId, "DRAFT", 1);
		}
	}

	private record StoredUpload(UUID fileId) {
	}

	private record Result(org.springframework.test.web.servlet.ResultActions actions) {

		Result andExpect(org.springframework.test.web.servlet.ResultMatcher matcher)
				throws Exception {
			actions.andExpect(matcher);
			return this;
		}

		JsonNode andReturnBody() throws Exception {
			return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class MutableClockConfiguration {

		@Bean
		@Primary
		MutableClock mutableFileClock() {
			return new MutableClock(Instant.parse("2026-08-30T00:00:00Z"));
		}
	}

	static final class MutableClock extends Clock {

		private final AtomicReference<Instant> current;

		MutableClock(Instant initial) {
			this.current = new AtomicReference<>(initial);
		}

		void set(Instant instant) {
			current.set(instant);
		}

		void advance(Duration duration) {
			current.updateAndGet(value -> value.plus(duration));
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current.get();
		}
	}
}
