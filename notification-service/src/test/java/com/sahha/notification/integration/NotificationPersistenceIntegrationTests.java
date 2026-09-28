package com.sahha.notification.integration;

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
class NotificationPersistenceIntegrationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesNotificationOwnedAppointmentProjection() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertEquals("sahha_notification", connection.getCatalog());
		}
		assertEquals(
				"notification_test",
				jdbcTemplate.queryForObject(
						"SELECT current_schema()", String.class));
		for (String table : new String[] {
			"consumed_appointment_event",
			"rejected_appointment_event",
			"appointment_notification_cursor",
			"consumed_communication_event",
			"rejected_communication_event",
			"consumed_referral_event",
			"referral_notification_cursor",
			"patient_appointment_notification",
			"in_app_notification"
		}) {
			assertEquals(
					table,
					jdbcTemplate.queryForObject(
							"SELECT to_regclass('notification_test."
									+ table + "')::text",
							String.class));
		}
		assertNotNull(flyway.info().current());
		assertEquals("5", flyway.info().current().getVersion().getVersion());
	}
}
