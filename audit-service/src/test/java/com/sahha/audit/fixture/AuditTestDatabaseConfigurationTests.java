package com.sahha.audit.fixture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuditTestDatabaseConfigurationTests {

	@Test
	void acceptsOnlyTheOwnedIsolatedSchemaIncludingItsFirstMigration() {
		for (String connectionSchema : new String[] { null, "audit_test" }) {
			assertDoesNotThrow(() -> AuditTestDatabaseConfiguration.validateTarget(
					"sahha_audit", "sahha_audit_app", "audit_test", new String[] { "audit_test" },
					true, connectionSchema));
		}
	}

	@ParameterizedTest
	@MethodSource("unsafeTargets")
	void rejectsUnsafeTargetsBeforeMigration(String database, String username, String schema,
			String[] schemas, boolean cleanDisabled, String connectionSchema) {
		assertThrows(IllegalStateException.class, () -> AuditTestDatabaseConfiguration.validateTarget(
				database, username, schema, schemas, cleanDisabled, connectionSchema));
	}

	static Stream<Arguments> unsafeTargets() {
		return Stream.of(
				Arguments.of("sahha_patient", "sahha_audit_app", "audit_test", new String[] { "audit_test" }, true, null),
				Arguments.of("sahha_audit", "postgres", "audit_test", new String[] { "audit_test" }, true, null),
				Arguments.of("sahha_audit", "sahha_audit_app", "public", new String[] { "audit_test" }, true, null),
				Arguments.of("sahha_audit", "sahha_audit_app", "audit_test", new String[] { "audit_test", "public" }, true, null),
				Arguments.of("sahha_audit", "sahha_audit_app", "audit_test", new String[0], true, null),
				Arguments.of("sahha_audit", "sahha_audit_app", "audit_test", new String[] { "audit_test" }, false, null),
				Arguments.of("sahha_audit", "sahha_audit_app", "audit_test", new String[] { "audit_test" }, true, "public"));
	}
}
