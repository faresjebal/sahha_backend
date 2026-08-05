package com.sahha.patient.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PatientPersistenceIntegrationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesPatientOwnedTablesAtCurrentVersion() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertEquals("sahha_patient", connection.getCatalog());
		}
		assertEquals(
				"patient_test",
				jdbcTemplate.queryForObject("SELECT current_schema()", String.class));
		for (String table : new String[] {
			"patient_identity",
			"patient_organisation_registration",
			"patient_audit_event",
			"patient_outbox_event"
		}) {
			assertEquals(
					table,
					jdbcTemplate.queryForObject(
							"SELECT to_regclass('patient_test." + table + "')::text",
							String.class));
		}
		assertNotNull(flyway.info().current());
		assertEquals("2", flyway.info().current().getVersion().getVersion());
	}

	@Test
	void auditRowsAreDatabaseAppendOnly() {
		jdbcTemplate.update("""
				INSERT INTO patient_audit_event (
				  id, organisation_id, actor_user_id, resource_type,
				  resource_id, event_type, result, metadata, occurred_at
				) VALUES (?, ?, ?, 'PATIENT_DIRECTORY', ?,
				  'PATIENT_DIRECTORY_SEARCHED', 'SUCCESS', '{}'::jsonb, now())
				""",
				java.util.UUID.randomUUID(),
				java.util.UUID.randomUUID(),
				java.util.UUID.randomUUID(),
				java.util.UUID.randomUUID());
		assertThrows(
				DataAccessException.class,
				() -> jdbcTemplate.update(
						"UPDATE patient_audit_event SET request_id = 'changed'"));
	}
}
