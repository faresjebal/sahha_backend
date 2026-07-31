package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.entity.AccountStatus;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.repository.UserAccountRepository;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserAccountPersistenceIntegrationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserAccountRepository repository;

	@BeforeAll
	void rebuildDedicatedTestDatabaseSchema() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertEquals(
					"sahha_auth_test",
					connection.getCatalog(),
					"Refusing to clean any database except sahha_auth_test");
		}

		flyway.clean();
		flyway.migrate();
	}

	@Test
	void migrationCreatesUserAccountTableAtCurrentVersion() {
		String tableName = jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.user_account')::text",
				String.class);

		assertEquals("user_account", tableName);
		assertNotNull(flyway.info().current());
		assertEquals("6", flyway.info().current().getVersion().getVersion());
	}

	@Test
	@Transactional
	void repositoryPersistsAndFindsPendingAccountByNormalizedEmail() {
		UserAccount account = pendingAccount("Doctor@Example.com", "doctor@example.com");

		repository.saveAndFlush(account);

		UserAccount persisted = repository.findByNormalizedEmail("doctor@example.com")
				.orElseThrow();
		assertEquals(account.getId(), persisted.getId());
		assertEquals(AccountStatus.PENDING_VERIFICATION, persisted.getStatus());
		assertEquals(1, persisted.getCredentialVersion());
		assertNotNull(persisted.getCreatedAt());
		assertNotNull(persisted.getUpdatedAt());
		assertTrue(repository.existsByNormalizedEmail("doctor@example.com"));
	}

	@Test
	@Transactional
	void databaseRejectsDuplicateNormalizedEmail() {
		repository.saveAndFlush(pendingAccount("doctor@example.com", "doctor@example.com"));

		UserAccount duplicate = pendingAccount("DOCTOR@example.com", "doctor@example.com");

		assertThrows(
				DataIntegrityViolationException.class,
				() -> repository.saveAndFlush(duplicate));
	}

	@Test
	@Transactional
	void databaseRejectsNonNormalizedEmailKey() {
		assertThrows(
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update(
						"""
						INSERT INTO user_account (
						    id,
						    email,
						    normalized_email,
						    password_hash,
						    first_name,
						    last_name,
						    status
						) VALUES (?, ?, ?, ?, ?, ?, ?)
						""",
						java.util.UUID.randomUUID(),
						"Doctor@Example.com",
						"Doctor@Example.com",
						"synthetic-password-hash",
						"Synthetic",
						"Doctor",
						AccountStatus.PENDING_VERIFICATION.name()));
	}

	private UserAccount pendingAccount(String email, String normalizedEmail) {
		return UserAccount.pendingRegistration(
				email,
				normalizedEmail,
				"synthetic-password-hash",
				"Synthetic",
				"Doctor",
				"+21600000000");
	}
}
