package com.sahha.audit.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.UUID;

import com.sahha.audit.fixture.AuditTestDatabaseConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(AuditTestDatabaseConfiguration.class)
@Transactional
class AuditPersistenceIntegrationTests {

	@Autowired JdbcTemplate jdbc;
	@Autowired Flyway flyway;

	@Test
	void validatesOwnedMigrationAndRestrictedLoginWithoutCleaningData() {
		assertEquals("sahha_audit", jdbc.queryForObject("SELECT current_database()", String.class));
		assertEquals("audit_test", jdbc.queryForObject("SELECT current_schema()", String.class));
		assertEquals("sahha_audit_app", jdbc.queryForObject("SELECT current_user", String.class));
		assertEquals(Boolean.FALSE, jdbc.queryForObject("""
				SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
				FROM pg_roles WHERE rolname = current_user
				""", Boolean.class));
		assertEquals(Boolean.FALSE, jdbc.queryForObject(
				"SELECT has_database_privilege(current_user, 'sahha_patient', 'CONNECT')", Boolean.class));
		flyway.validate();
		assertEquals("1", flyway.info().current().getVersion().getVersion());
		assertEquals(0, flyway.info().pending().length);
		assertTrue(flyway.getConfiguration().isCleanDisabled());
		assertEquals(0, jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.table_constraints
				WHERE table_schema = 'audit_test' AND table_name = 'audit_event'
				AND constraint_type = 'FOREIGN KEY'
				""", Integer.class));
	}

	@Test
	void roundTripsMinimalOrganisationAndGlobalEventsWithDatabaseReceiptTime() {
		UUID id = UUID.randomUUID();
		insertOrganisationEvent(id, UUID.randomUUID());
		assertEquals("CONSULTATION_VIEWED", jdbc.queryForObject(
				"SELECT event_type FROM audit_event WHERE id = ?", String.class, id));
		assertNotNull(jdbc.queryForObject("SELECT recorded_at FROM audit_event WHERE id = ?",
				java.sql.Timestamp.class, id));
		assertNotNull(jdbc.queryForObject("SELECT organisation_id FROM audit_event WHERE id = ?", UUID.class, id));
		UUID globalId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO audit_event (id, source_service, source_event_id, event_type, scope,
				resource_type, result, occurred_at)
				VALUES (?, 'auth-service', ?, 'LOGIN_DENIED', 'GLOBAL', 'USER_ACCOUNT', 'DENIED', now())
				""", globalId, UUID.randomUUID());
		assertNull(jdbc.queryForObject("SELECT actor_user_id FROM audit_event WHERE id = ?", UUID.class, globalId));
		assertNull(jdbc.queryForObject("SELECT organisation_id FROM audit_event WHERE id = ?", UUID.class, globalId));
	}

	@Test
	void sourceEventIdentityIsUniqueEvenWhenNewCentralIdIsSupplied() {
		UUID sourceId = UUID.randomUUID();
		insertOrganisationEvent(UUID.randomUUID(), sourceId);
		assertSqlState("23505", () -> insertOrganisationEvent(UUID.randomUUID(), sourceId));
	}

	@Test
	void equalSourceIdsFromDifferentServiceOwnersDoNotCollide() {
		UUID sourceId = UUID.randomUUID();
		insertOrganisationEvent(UUID.randomUUID(), sourceId);
		jdbc.update("""
				INSERT INTO audit_event (id, source_service, source_event_id, event_type, scope,
				resource_type, result, occurred_at)
				VALUES (?, 'auth-service', ?, 'LOGIN_SUCCEEDED', 'GLOBAL', 'USER_ACCOUNT', 'SUCCESS', now())
				""", UUID.randomUUID(), sourceId);
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE source_event_id = ?",
				Integer.class, sourceId));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"UPDATE audit_event SET result = 'DENIED' WHERE id = ?",
			"DELETE FROM audit_event WHERE id = ?",
			"TRUNCATE TABLE audit_event",
			"UPDATE audit_event SET result = 'DENIED' WHERE false",
			"DELETE FROM audit_event WHERE false"
	})
	void databaseRejectsMutationIncludingNoOpStatements(String sql) {
		UUID id = UUID.randomUUID();
		insertOrganisationEvent(id, UUID.randomUUID());
		assertSqlState("55000", () -> {
			if (sql.contains("?")) jdbc.update(sql, id);
			else jdbc.execute(sql);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"'ORGANISATION', NULL, NULL, 'EVENT_READ', 'RESOURCE', 'SUCCESS', NULL, NULL",
			"'GLOBAL', '00000000-0000-0000-0000-000000000001'::uuid, NULL, 'EVENT_READ', 'RESOURCE', 'SUCCESS', NULL, NULL",
			"'GLOBAL', NULL, '00000000-0000-0000-0000-000000000001'::uuid, 'EVENT_READ', 'RESOURCE', 'SUCCESS', NULL, NULL",
			"'UNKNOWN', NULL, NULL, 'EVENT_READ', 'RESOURCE', 'SUCCESS', NULL, NULL",
			"'GLOBAL', NULL, NULL, 'clinical narrative', 'RESOURCE', 'SUCCESS', NULL, NULL",
			"'GLOBAL', NULL, NULL, 'EVENT_READ', '', 'SUCCESS', NULL, NULL",
			"'GLOBAL', NULL, NULL, 'EVENT_READ', 'RESOURCE', 'UNKNOWN', NULL, NULL",
			"'GLOBAL', NULL, NULL, 'EVENT_READ', 'RESOURCE', 'SUCCESS', 'free text reason', NULL",
			"'GLOBAL', NULL, NULL, 'EVENT_READ', 'RESOURCE', 'SUCCESS', NULL, E'unsafe\nrequest'"
	})
	void databaseRejectsInvalidScopeAndUnstructuredValues(String syntheticValues) {
		// Values are compile-time synthetic test fixtures, never external input.
		assertSqlState("23514", () -> jdbc.update("""
				INSERT INTO audit_event (id, source_service, source_event_id, occurred_at,
				scope, organisation_id, patient_id, event_type, resource_type, result, access_reason_code, request_id)
				VALUES (?, 'auth-service', ?, now(),
				""" + syntheticValues + ")", UUID.randomUUID(), UUID.randomUUID()));
	}

	@Test
	void databaseRejectsUnknownProducers() {
		assertSqlState("23514", () -> jdbc.update("""
				INSERT INTO audit_event (id, source_service, source_event_id, event_type, scope,
				resource_type, result, occurred_at)
				VALUES (?, 'browser', ?, 'EVENT_READ', 'GLOBAL', 'RESOURCE', 'SUCCESS', now())
				""", UUID.randomUUID(), UUID.randomUUID()));
	}

	private void insertOrganisationEvent(UUID id, UUID sourceId) {
		jdbc.update("""
				INSERT INTO audit_event (id, source_service, source_event_id, event_type, scope,
				organisation_id, actor_user_id, resource_type, resource_id, patient_id,
				result, access_reason_code, request_id, occurred_at)
				VALUES (?, 'clinical-service', ?, 'CONSULTATION_VIEWED', 'ORGANISATION',
				?, ?, 'CONSULTATION', ?, ?, 'SUCCESS', 'TREATING_DOCTOR', 'synthetic-audit-test',
				'2026-09-13T08:00:00Z')
				""", id, sourceId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
	}

	private void assertSqlState(String expected, Runnable action) {
		DataAccessException failure = assertThrows(DataAccessException.class, action::run);
		SQLException sql = assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
		assertEquals(expected, sql.getSQLState());
	}
}
