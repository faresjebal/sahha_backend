package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.entity.RefreshToken;
import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.repository.RefreshTokenRepository;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.repository.UserSessionRepository;

@SpringBootTest
class UserSessionPersistenceIntegrationTests {

	private static final Instant CREATED_AT = Instant.parse("2026-07-01T08:00:00Z");

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private UserSessionRepository sessionRepository;

	@Autowired
	private RefreshTokenRepository tokenRepository;

	@Test
	void migrationCreatesSessionAndRefreshTokenTablesAtCurrentVersion() {
		String sessionTable = jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.user_session')::text",
				String.class);
		String tokenTable = jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.refresh_token')::text",
				String.class);

		assertEquals("user_session", sessionTable);
		assertEquals("refresh_token", tokenTable);
		assertNotNull(flyway.info().current());
		assertEquals("7", flyway.info().current().getVersion().getVersion());
	}

	@Test
	@Transactional
	void persistsOneSessionFamilyAndItsInitialHashedToken() {
		UserAccount user = persistedUser("session-owner@example.com");
		UserSession session = sessionRepository.saveAndFlush(
				session(user, "a".repeat(64), "Laptop"));
		RefreshToken token = tokenRepository.saveAndFlush(
				initialToken(session, "b".repeat(64)));

		assertEquals(SessionStatus.ACTIVE, session.getStatus());
		assertEquals(user.getCredentialVersion(), session.getCredentialVersionAtCreation());
		assertEquals(user.getId(), token.getUser().getId());
		assertEquals(session.getId(), token.getSession().getId());
		assertTrue(token.isActiveAt(CREATED_AT.plusSeconds(60)));
		assertEquals(
				session.getId(),
				sessionRepository.findByIdForUpdate(session.getId()).orElseThrow().getId());
		assertEquals(
				token.getId(),
				tokenRepository.findByTokenHashForUpdate(token.getTokenHash())
						.orElseThrow()
						.getId());
		assertEquals(
				token.getId(),
				tokenRepository
						.findBySession_IdAndUsedAtIsNullAndRevokedAtIsNull(
								session.getId())
						.orElseThrow()
						.getId());
	}

	@Test
	@Transactional
	void separateDevicesReceiveIndependentSessionFamilies() {
		UserAccount user = persistedUser("multiple-devices@example.com");
		UserSession laptop = sessionRepository.saveAndFlush(
				session(user, "c".repeat(64), "Laptop"));
		UserSession phone = sessionRepository.saveAndFlush(
				session(user, "d".repeat(64), "Phone"));
		RefreshToken laptopToken = tokenRepository.saveAndFlush(
				initialToken(laptop, "e".repeat(64)));
		RefreshToken phoneToken = tokenRepository.saveAndFlush(
				initialToken(phone, "f".repeat(64)));

		assertNotEquals(laptop.getId(), phone.getId());
		assertNotEquals(laptopToken.getId(), phoneToken.getId());
		assertEquals(
				2,
				sessionRepository
						.findAllByUser_IdAndStatusOrderByCreatedAtDesc(
								user.getId(),
								SessionStatus.ACTIVE)
						.size());
		assertEquals(1, tokenRepository.findAllBySession_IdOrderByCreatedAtAsc(
				laptop.getId()).size());
		assertEquals(1, tokenRepository.findAllBySession_IdOrderByCreatedAtAsc(
				phone.getId()).size());
	}

	@Test
	@Transactional
	void recordsActivityWithoutExtendingPastAbsoluteExpiryAndRevokesExplicitly() {
		UserAccount user = persistedUser("session-lifecycle@example.com");
		UserSession session = sessionRepository.saveAndFlush(
				session(user, "1".repeat(64), "Workstation"));
		Instant activityAt = CREATED_AT.plusSeconds(86_400);
		Instant extendedIdleExpiration = CREATED_AT.plusSeconds(691_200);

		session.recordActivity(
				activityAt,
				extendedIdleExpiration,
				"192.0.2.20");
		sessionRepository.saveAndFlush(session);

		assertEquals(activityAt, session.getLastActivityAt());
		assertEquals(extendedIdleExpiration, session.getIdleExpiresAt());
		assertEquals("192.0.2.20", session.getLastIp());
		assertThrows(
				IllegalArgumentException.class,
				() -> session.recordActivity(
						activityAt.plusSeconds(1),
						session.getAbsoluteExpiresAt().plusSeconds(1),
						"192.0.2.21"));

		Instant revokedAt = CREATED_AT.plusSeconds(172_800);
		session.revoke(revokedAt, user, "USER_LOGOUT");
		sessionRepository.saveAndFlush(session);

		assertEquals(SessionStatus.REVOKED, session.getStatus());
		assertEquals(revokedAt, session.getRevokedAt());
		assertEquals(user.getId(), session.getRevokedBy().getId());
		assertEquals("USER_LOGOUT", session.getRevocationReason());
		assertFalse(sessionRepository
				.findAllByUser_IdAndStatusOrderByCreatedAtDesc(
						user.getId(),
						SessionStatus.ACTIVE)
				.stream()
				.anyMatch(candidate -> candidate.getId().equals(session.getId())));
	}

	@Test
	@Transactional
	void databaseRejectsAnIdleExpirationPastTheAbsoluteExpiration() {
		UserAccount user = persistedUser("invalid-session-expiry@example.com");

		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertSession(
						UUID.randomUUID(),
						user.getId(),
						"a".repeat(64),
						SessionStatus.ACTIVE,
						CREATED_AT.plusSeconds(2_592_001),
						CREATED_AT.plusSeconds(2_592_000),
						null,
						null));
	}

	@Test
	@Transactional
	void databaseRejectsARevokedSessionWithoutRevocationEvidence() {
		UserAccount user = persistedUser("invalid-session-revocation@example.com");

		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertSession(
						UUID.randomUUID(),
						user.getId(),
						"b".repeat(64),
						SessionStatus.REVOKED,
						CREATED_AT.plusSeconds(604_800),
						CREATED_AT.plusSeconds(2_592_000),
						null,
						null));
	}

	@Test
	@Transactional
	void databaseAllowsOnlyOneActiveRefreshTokenPerSession() {
		UserAccount user = persistedUser("single-active-token@example.com");
		UserSession session = sessionRepository.saveAndFlush(
				session(user, "2".repeat(64), "Laptop"));
		tokenRepository.saveAndFlush(initialToken(session, "3".repeat(64)));

		assertThrows(
				DataIntegrityViolationException.class,
				() -> tokenRepository.saveAndFlush(
						initialToken(session, "4".repeat(64))));
	}

	@Test
	@Transactional
	void databaseRequiresTokenHashesToBeGloballyUnique() {
		UserAccount user = persistedUser("unique-token-hash@example.com");
		UserSession firstSession = sessionRepository.saveAndFlush(
				session(user, "5".repeat(64), "Laptop"));
		UserSession secondSession = sessionRepository.saveAndFlush(
				session(user, "6".repeat(64), "Phone"));
		tokenRepository.saveAndFlush(initialToken(firstSession, "7".repeat(64)));

		assertThrows(
				DataIntegrityViolationException.class,
				() -> tokenRepository.saveAndFlush(
						initialToken(secondSession, "7".repeat(64))));
	}

	@Test
	@Transactional
	void persistsOneToOneRotationLineageInsideTheSessionFamily() {
		UserAccount user = persistedUser("token-rotation@example.com");
		UserSession session = sessionRepository.saveAndFlush(
				session(user, "8".repeat(64), "Laptop"));
		RefreshToken initial = tokenRepository.saveAndFlush(
				initialToken(session, "9".repeat(64)));
		Instant rotationTime = CREATED_AT.plusSeconds(3_600);

		initial.consumeForRotation(rotationTime);
		tokenRepository.saveAndFlush(initial);

		RefreshToken replacement = RefreshToken.issueReplacement(
				session,
				initial,
				"a".repeat(64),
				rotationTime,
				CREATED_AT.plusSeconds(1_209_600),
				"192.0.2.30",
				"Synthetic browser");
		tokenRepository.saveAndFlush(replacement);

		initial.linkReplacement(replacement);
		tokenRepository.saveAndFlush(initial);

		assertEquals(rotationTime, initial.getUsedAt());
		assertEquals("ROTATED", initial.getRevocationReason());
		assertEquals(replacement.getId(), initial.getReplacedByToken().getId());
		assertEquals(initial.getId(), replacement.getParentToken().getId());
		assertEquals(
				replacement.getId(),
				tokenRepository
						.findBySession_IdAndUsedAtIsNullAndRevokedAtIsNull(
								session.getId())
						.orElseThrow()
						.getId());
		assertEquals(
				2,
				tokenRepository.findAllBySession_IdOrderByCreatedAtAsc(
						session.getId()).size());
	}

	@Test
	@Transactional
	void databaseRejectsATokenWhoseUserDiffersFromItsSessionOwner() {
		UserAccount sessionOwner = persistedUser("actual-session-owner@example.com");
		UserAccount differentUser = persistedUser("different-token-user@example.com");
		UserSession session = sessionRepository.saveAndFlush(
				session(sessionOwner, "b".repeat(64), "Laptop"));

		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertToken(
						UUID.randomUUID(),
						session.getId(),
						differentUser.getId(),
						"c".repeat(64),
						null));
	}

	@Test
	@Transactional
	void databaseRejectsAParentTokenFromAnotherSessionFamily() {
		UserAccount user = persistedUser("cross-family-parent@example.com");
		UserSession firstSession = sessionRepository.saveAndFlush(
				session(user, "d".repeat(64), "Laptop"));
		UserSession secondSession = sessionRepository.saveAndFlush(
				session(user, "e".repeat(64), "Phone"));
		RefreshToken firstToken = tokenRepository.saveAndFlush(
				initialToken(firstSession, "f".repeat(64)));
		firstToken.consumeForRotation(CREATED_AT.plusSeconds(3_600));
		tokenRepository.saveAndFlush(firstToken);

		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertToken(
						UUID.randomUUID(),
						secondSession.getId(),
						user.getId(),
						"0".repeat(64),
						firstToken.getId()));
	}

	private UserAccount persistedUser(String email) {
		return userRepository.saveAndFlush(UserAccount.pendingRegistration(
				email,
				email,
				"synthetic-password-hash",
				"Synthetic",
				"User",
				null));
	}

	private UserSession session(
			UserAccount user,
			String deviceHash,
			String deviceName) {
		return UserSession.open(
				user,
				deviceHash,
				deviceName,
				"Synthetic browser",
				"192.0.2.10",
				CREATED_AT,
				CREATED_AT.plusSeconds(604_800),
				CREATED_AT.plusSeconds(2_592_000));
	}

	private RefreshToken initialToken(UserSession session, String tokenHash) {
		return RefreshToken.issueInitial(
				session,
				tokenHash,
				CREATED_AT.plusSeconds(1),
				CREATED_AT.plusSeconds(604_800),
				"192.0.2.10",
				"Synthetic browser");
	}

	private void insertSession(
			UUID id,
			UUID userId,
			String deviceHash,
			SessionStatus status,
			Instant idleExpiresAt,
			Instant absoluteExpiresAt,
			Instant revokedAt,
			String revocationReason) {
		jdbcTemplate.update(
				"""
				INSERT INTO user_session (
				    id,
				    user_id,
				    status,
				    device_id_hash,
				    created_at,
				    last_activity_at,
				    idle_expires_at,
				    absolute_expires_at,
				    revoked_at,
				    revocation_reason,
				    credential_version_at_creation,
				    updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				id,
				userId,
				status.name(),
				deviceHash,
				Timestamp.from(CREATED_AT),
				Timestamp.from(CREATED_AT),
				Timestamp.from(idleExpiresAt),
				Timestamp.from(absoluteExpiresAt),
				revokedAt == null ? null : Timestamp.from(revokedAt),
				revocationReason,
				1,
				Timestamp.from(CREATED_AT));
	}

	private void insertToken(
			UUID id,
			UUID sessionId,
			UUID userId,
			String tokenHash,
			UUID parentTokenId) {
		jdbcTemplate.update(
				"""
				INSERT INTO refresh_token (
				    id,
				    session_id,
				    user_id,
				    token_hash,
				    parent_token_id,
				    created_at,
				    expires_at
				) VALUES (?, ?, ?, ?, ?, ?, ?)
				""",
				id,
				sessionId,
				userId,
				tokenHash,
				parentTokenId,
				Timestamp.from(CREATED_AT.plusSeconds(7_200)),
				Timestamp.from(CREATED_AT.plusSeconds(604_800)));
	}
}
