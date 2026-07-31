package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.entity.AuthOutboxEvent;
import com.sahha.auth.entity.SecurityEvent;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.repository.AuthOutboxEventRepository;
import com.sahha.auth.repository.SecurityEventRepository;
import com.sahha.auth.service.useraccountservice.AccountRegistrationService;
import com.sahha.auth.service.useraccountservice.AccountVerificationService;
import com.sahha.auth.service.useraccountservice.PendingRegistrationResult;
import com.sahha.auth.service.usersessionservice.UserSessionService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class SecurityEventOutboxIntegrationTests {

	private static final String PASSWORD =
			"Synthetic audited passphrase 2026!";

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AccountRegistrationService registrationService;

	@Autowired
	private AccountVerificationService verificationService;

	@Autowired
	private UserSessionService sessionService;

	@Autowired
	private SecurityEventRepository eventRepository;

	@Autowired
	private AuthOutboxEventRepository outboxRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void migrationCreatesAppendOnlyEventsAndTransactionalOutbox() {
		assertEquals(
				"security_event",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('public.security_event')::text",
						String.class));
		assertEquals(
				"auth_outbox_event",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('public.auth_outbox_event')::text",
						String.class));
		assertEquals(
				"6",
				flyway.info().current().getVersion().getVersion());
	}

	@Test
	void registrationVerificationAndLoginCreateSecretFreeOutboxRows()
			throws Exception {
		Instant registeredAt = Instant.now().minusSeconds(5);
		String email = "security-event-" + UUID.randomUUID()
				+ "@example.com";
		PendingRegistrationResult registration = registrationService.register(
				email,
				PASSWORD,
				"Synthetic",
				"Audit",
				null,
				registeredAt);
		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				registeredAt.plusSeconds(1));
		assertTrue(sessionService.login(
				email,
				PASSWORD,
				"synthetic-device-id",
				"Laptop",
				"Synthetic browser",
				"192.0.2.44",
				registeredAt.plusSeconds(2))
				.isPresent());

		List<SecurityEvent> events =
				eventRepository.findAllByUserIdOrderByOccurredAtAsc(
						registration.getUserId());
		assertTrue(events.stream().map(SecurityEvent::getEventType).toList()
				.containsAll(List.of(
						SecurityEventType.ACCOUNT_REGISTERED,
						SecurityEventType.EMAIL_VERIFIED,
						SecurityEventType.LOGIN_SUCCEEDED)));
		for (SecurityEvent event : events) {
			AuthOutboxEvent outbox = outboxRepository
					.findBySecurityEventId(event.getId())
					.orElseThrow();
			assertEquals(event.getEventType().name(), outbox.getEventType());
			assertEquals(0, outbox.getPublicationAttempts());
			assertNotNull(outbox.getPayload().get("eventId"));
			String payload = objectMapper.writeValueAsString(
					outbox.getPayload());
			assertTrue(!payload.contains(PASSWORD));
			assertTrue(!payload.contains(
					registration.getVerificationToken().getRawToken()));
		}
	}

	@Test
	@Transactional
	void databaseRejectsSecurityEventMutation() {
		Instant registeredAt = Instant.now().minusSeconds(2);
		PendingRegistrationResult registration = registrationService.register(
				"append-only-" + UUID.randomUUID() + "@example.com",
				PASSWORD,
				"Synthetic",
				"Audit",
				null,
				registeredAt);
		SecurityEvent event =
				eventRepository.findAllByUserIdOrderByOccurredAtAsc(
						registration.getUserId())
						.getFirst();

		assertThrows(
				DataAccessException.class,
				() -> jdbcTemplate.update(
						"UPDATE security_event SET reason_code = 'TAMPERED' WHERE id = ?",
						event.getId()));
	}
}
