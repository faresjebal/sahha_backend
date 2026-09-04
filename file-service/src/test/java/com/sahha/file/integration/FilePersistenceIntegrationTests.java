package com.sahha.file.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.file.entity.FileAuditEvent;
import com.sahha.file.entity.FileAuditEventType;
import com.sahha.file.entity.FileAuditResult;
import com.sahha.file.entity.FileDownloadGrant;
import com.sahha.file.entity.FileOutboxEvent;
import com.sahha.file.entity.FileScanStatus;
import com.sahha.file.entity.FileUploadStatus;
import com.sahha.file.entity.MedicalFile;
import com.sahha.file.repository.FileAuditEventRepository;
import com.sahha.file.repository.FileDownloadGrantRepository;
import com.sahha.file.repository.FileOutboxEventRepository;
import com.sahha.file.repository.MedicalFileRepository;
import com.sahha.file.storage.PrivateObjectStorage;

@SpringBootTest
@Transactional
class FilePersistenceIntegrationTests {

	private static final String CONTENT_CHECKSUM =
			"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

	@Autowired private Flyway flyway;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private MedicalFileRepository fileRepository;
	@Autowired private FileAuditEventRepository auditRepository;
	@Autowired private FileDownloadGrantRepository grantRepository;
	@Autowired private FileOutboxEventRepository outboxRepository;
	@Autowired private PrivateObjectStorage objectStorage;

	@Test
	void migrationAndHibernateModelPreservePrivateFileLifecycle() {
		assertEquals("2", flyway.info().current().getVersion().getVersion());
		assertEquals(4, jdbcTemplate.queryForObject("""
				select count(*)
				from information_schema.tables
				where table_schema = 'file_test'
				  and table_name in (
				      'medical_file', 'file_download_grant',
				      'file_audit_event', 'file_outbox_event'
				  )
				""", Integer.class));

		Instant createdAt = Instant.parse("2026-08-24T21:00:00Z");
		MedicalFile file = fileRepository.saveAndFlush(MedicalFile.negotiate(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"synthetic-report.pdf", "application/pdf", 1,
				null, "organisation/consultation/file-id",
				"a".repeat(64), createdAt, createdAt.plusSeconds(300)));
		assertEquals(FileUploadStatus.NEGOTIATED, file.getUploadStatus());
		assertEquals(FileScanStatus.PENDING, file.getScanStatus());
		assertFalse(file.isAvailable());

		file.consumeUploadTicket(createdAt.plusSeconds(10));
		file.markStored(1, "b".repeat(64), createdAt.plusSeconds(11));
		file.markClean(createdAt.plusSeconds(12));
		fileRepository.saveAndFlush(file);
		assertTrue(file.isAvailable());

		FileAuditEvent audit = auditRepository.saveAndFlush(FileAuditEvent.record(
				file, file.getUploaderUserId(), FileAuditEventType.SCAN_MARKED_CLEAN,
				FileAuditResult.SUCCEEDED, "SYNTHETIC_LOCAL_CLEAN",
				"phase-five-d-foundation", createdAt.plusSeconds(12)));
		FileOutboxEvent outbox = outboxRepository.saveAndFlush(
				FileOutboxEvent.pending(
						audit, file, "medical-file.available.v1",
						Map.of(
								"eventId", UUID.randomUUID().toString(),
								"eventType", "medical-file.available.v1",
								"schemaVersion", 1,
								"fileId", file.getId().toString(),
								"organisationId", file.getOrganisationId().toString(),
								"consultationId", file.getConsultationId().toString(),
								"resourceVersion", file.getVersion())));
		assertEquals(1, auditRepository.countByMedicalFileId(file.getId()));
		assertEquals(1, outboxRepository.countByMedicalFileId(file.getId()));
		assertFalse(outbox.getPayload().containsKey("filename"));
		assertFalse(outbox.getPayload().containsKey("checksum"));
		UUID firstClaim = UUID.randomUUID();
		outbox.claim(firstClaim, createdAt.plusSeconds(13), Duration.ofMinutes(1));
		outbox.markFailed(
				firstClaim, createdAt.plusSeconds(14), Duration.ofSeconds(5),
				"SyntheticKafkaFailure");
		outboxRepository.saveAndFlush(outbox);
		assertEquals(1, outbox.getPublicationAttempts());
		assertEquals(createdAt.plusSeconds(19), outbox.getNextAttemptAt());
		assertEquals(null, outbox.getClaimToken());
		UUID secondClaim = UUID.randomUUID();
		outbox.claim(secondClaim, createdAt.plusSeconds(19), Duration.ofMinutes(1));
		outbox.markPublished(secondClaim, createdAt.plusSeconds(20));
		outboxRepository.saveAndFlush(outbox);
		assertEquals(2, outbox.getPublicationAttempts());
		assertEquals(createdAt.plusSeconds(20), outbox.getPublishedAt());

		FileDownloadGrant grant = grantRepository.saveAndFlush(
				FileDownloadGrant.issue(
						file, file.getUploaderUserId(), "c".repeat(64),
						"phase-five-d-grant", createdAt.plusSeconds(13),
						createdAt.plusSeconds(133)));
		grant.consume(createdAt.plusSeconds(14));
		grantRepository.saveAndFlush(grant);
		assertEquals(createdAt.plusSeconds(14), grant.getUsedAt());

		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
				"delete from file_audit_event where id = ?", audit.getId()));
	}

	@Test
	void inMemoryStorageRemainsPrivateAndRoundTripsBytes() throws Exception {
		byte[] content = "synthetic medical document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		String storageKey = "test/" + UUID.randomUUID();
		objectStorage.put(
				storageKey, new ByteArrayInputStream(content), content.length,
				"application/pdf");
		assertTrue(objectStorage.exists(storageKey));
		try (var stored = objectStorage.get(storageKey)) {
			assertArrayEquals(content, stored.readAllBytes());
		}
		objectStorage.remove(storageKey);
		assertFalse(objectStorage.exists(storageKey));
	}

	@Test
	void unsafeMetadataAndMismatchedIntegrityAreRejected() {
		Instant now = Instant.parse("2026-08-24T21:00:00Z");
		assertThrows(IllegalArgumentException.class, () -> MedicalFile.negotiate(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"../report.pdf", "application/pdf", 1, CONTENT_CHECKSUM,
				"storage-key", "a".repeat(64), now, now.plusSeconds(300)));

		MedicalFile file = MedicalFile.negotiate(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"report.pdf", "application/pdf", 1, CONTENT_CHECKSUM,
				"storage-key", "a".repeat(64), now, now.plusSeconds(300));
		file.consumeUploadTicket(now.plusSeconds(1));
		assertThrows(IllegalArgumentException.class,
				() -> file.markStored(2, CONTENT_CHECKSUM, now.plusSeconds(2)));
		assertThrows(IllegalArgumentException.class,
				() -> file.markStored(1, "b".repeat(64), now.plusSeconds(2)));
	}
}
