package com.sahha.organisation.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.dto.request.CreateOrganisationRequest;
import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.entity.OrganisationAuditEvent;
import com.sahha.organisation.entity.OrganisationType;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.service.organisationservice.OrganisationService;

@SpringBootTest
@Transactional
class OrganisationPersistenceIntegrationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OrganisationService organisationService;

	@Autowired
	private OrganisationAuditEventRepository auditRepository;

	@Autowired
	private OrganisationOutboxEventRepository outboxRepository;

	@Test
	void migrationCreatesOwnedTablesAtCurrentVersion() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertEquals("sahha_organisation", connection.getCatalog());
		}
		assertEquals(
				"organisation_test",
				jdbcTemplate.queryForObject(
						"SELECT current_schema()",
						String.class));
		assertEquals(
				"organisation",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.organisation')::text",
						String.class));
		assertEquals(
				"organisation_audit_event",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.organisation_audit_event')::text",
						String.class));
		assertEquals(
				"organisation_outbox_event",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.organisation_outbox_event')::text",
						String.class));
		assertNotNull(flyway.info().current());
		assertEquals("2", flyway.info().current().getVersion().getVersion());
	}

	@Test
	void creationPersistsOrganisationAuditAndSecretFreeOutboxAtomically() {
		UUID actor = UUID.randomUUID();
		OrganisationResponse created = organisationService.create(
				request("Persistence " + UUID.randomUUID()),
				actor,
				"organisation-persistence-request");

		assertEquals(actor, created.createdBy());
		assertEquals("TN", created.countryCode());
		assertEquals("Africa/Tunis", created.timeZone());
		assertEquals(1, auditRepository.countByOrganisationId(created.id()));
		OrganisationAuditEvent audit = auditRepository.findAll()
				.stream()
				.filter(event -> event.getOrganisationId().equals(created.id()))
				.findFirst()
				.orElseThrow();
		var outbox = outboxRepository.findByAuditEventId(audit.getId())
				.orElseThrow();
		assertEquals(created.id(), outbox.getOrganisationId());
		assertEquals(
				"ORGANISATION_CREATED",
				outbox.getPayload().get("eventType"));
		assertFalse(outbox.getPayload().containsKey("contactEmail"));
		assertFalse(outbox.getPayload().containsKey("address"));
		assertFalse(outbox.getPayload().containsKey("phoneNumber"));
	}

	@Test
	void databaseRejectsDuplicateNormalizedOrganisationName() {
		String name = "Duplicate " + UUID.randomUUID();
		organisationService.create(
				request(name),
				UUID.randomUUID(),
				"organisation-duplicate-first");

		assertThrows(
				DataAccessException.class,
				() -> organisationService.create(
						request("  " + name.toUpperCase() + "  "),
						UUID.randomUUID(),
						"organisation-duplicate-second"));
	}

	@Test
	void auditRowsAreAppendOnly() {
		OrganisationResponse created = organisationService.create(
				request("Audit " + UUID.randomUUID()),
				UUID.randomUUID(),
				"organisation-audit-request");
		OrganisationAuditEvent audit = auditRepository.findAll()
				.stream()
				.filter(event -> event.getOrganisationId().equals(created.id()))
				.findFirst()
				.orElseThrow();

		assertThrows(
				DataAccessException.class,
				() -> jdbcTemplate.update(
						"DELETE FROM organisation_audit_event WHERE id = ?",
						audit.getId()));
	}

	private static CreateOrganisationRequest request(String name) {
		return new CreateOrganisationRequest(
				name,
				name + " Legal",
				OrganisationType.CLINIC,
				"contact@example.com",
				"+216 71 000 000",
				"12 Synthetic Avenue",
				"Tunis",
				"Tunis",
				"1000",
				"tn",
				"Africa/Tunis");
	}
}
