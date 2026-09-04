package com.sahha.scheduling.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SchedulingPersistenceIntegrationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesSchedulingOwnedAvailabilityTables() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertEquals("sahha_scheduling", connection.getCatalog());
		}
		assertEquals(
				"scheduling_test",
				jdbcTemplate.queryForObject("SELECT current_schema()", String.class));
		for (String table : new String[] {
			"doctor_availability_schedule",
			"doctor_weekly_availability",
			"doctor_availability_break",
			"doctor_time_off",
			"scheduled_appointment",
			"appointment_audit_event",
			"appointment_outbox_event",
			"consumed_clinical_event"
		}) {
			assertEquals(
					table,
					jdbcTemplate.queryForObject(
							"SELECT to_regclass('scheduling_test." + table + "')::text",
							String.class));
		}
		assertNotNull(flyway.info().current());
		assertEquals("7", flyway.info().current().getVersion().getVersion());
	}
}
