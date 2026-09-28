package com.sahha.audit.fixture;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Refuse an unsafe target before Flyway is allowed to create any objects. */
@TestConfiguration(proxyBeanMethods = false)
public class AuditTestDatabaseConfiguration {

	@Bean
	FlywayMigrationStrategy auditTestMigrationStrategy() {
		return flyway -> {
			var configuration = flyway.getConfiguration();
			try (Connection connection = configuration.getDataSource().getConnection()) {
				validateTarget(connection.getCatalog(), connection.getMetaData().getUserName(),
						configuration.getDefaultSchema(), configuration.getSchemas(),
						configuration.isCleanDisabled(), connection.getSchema());
			} catch (SQLException failure) {
				// Do not echo URLs, credentials or driver connection details.
				throw new IllegalStateException("Cannot verify the isolated Audit test database");
			}
			flyway.migrate();
		};
	}

	static void validateTarget(String database, String username, String defaultSchema,
			String[] schemas, boolean cleanDisabled, String connectionSchema) {
		if (!"sahha_audit".equals(database) || !"sahha_audit_app".equals(username)
				|| !"audit_test".equals(defaultSchema)
				|| !Arrays.equals(new String[] { "audit_test" }, schemas) || !cleanDisabled
				|| (connectionSchema != null && !"audit_test".equals(connectionSchema))) {
			throw new IllegalStateException("Audit tests require the owned database/login, "
					+ "only the audit_test schema, and Flyway clean disabled");
		}
	}
}
