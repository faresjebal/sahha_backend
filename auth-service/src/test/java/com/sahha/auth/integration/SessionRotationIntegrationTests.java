package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sahha.auth.config.AuthSecurityProperties;
import com.sahha.auth.entity.RefreshToken;
import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.exception.InvalidRefreshTokenException;
import com.sahha.auth.repository.RefreshTokenRepository;
import com.sahha.auth.repository.UserSessionRepository;
import com.sahha.auth.security.TokenHashingService;
import com.sahha.auth.service.refreshtokenservice.RefreshTokenRotationService;
import com.sahha.auth.service.useraccountservice.AccountRegistrationService;
import com.sahha.auth.service.useraccountservice.AccountVerificationService;
import com.sahha.auth.service.useraccountservice.PendingRegistrationResult;
import com.sahha.auth.service.usersessionservice.IssuedSessionCredentials;
import com.sahha.auth.service.usersessionservice.UserSessionService;
import com.sahha.auth.service.verificationtokenservice.IssuedVerificationToken;

@SpringBootTest
class SessionRotationIntegrationTests {

	private static final Instant REGISTERED_AT =
			Instant.parse("2026-07-27T00:00:00Z");
	private static final String PASSWORD =
			"synthetic session passphrase";
	private static final String NEW_PASSWORD =
			"synthetic replacement session passphrase";

	@Autowired
	private AccountRegistrationService registrationService;

	@Autowired
	private AccountVerificationService verificationService;

	@Autowired
	private UserSessionService sessionService;

	@Autowired
	private RefreshTokenRotationService rotationService;

	@Autowired
	private UserSessionRepository sessionRepository;

	@Autowired
	private RefreshTokenRepository tokenRepository;

	@Autowired
	private TokenHashingService tokenHashingService;

	@Autowired
	private AuthSecurityProperties securityProperties;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void verifiedLoginCreatesOneSessionFamilyAndStoresOnlyTheRefreshHash() {
		VerifiedAccount account = verifiedAccount("login-session");
		Instant loggedInAt = REGISTERED_AT.plusSeconds(120);

		IssuedSessionCredentials credentials = login(
				account,
				"synthetic-laptop-device-id",
				"Laptop",
				loggedInAt);

		UserSession session = sessionRepository
				.findById(credentials.getSessionId())
				.orElseThrow();
		RefreshToken token = tokenRepository
				.findByTokenHash(
						tokenHashingService.hash(
								credentials.getRawRefreshToken()))
				.orElseThrow();

		assertEquals(account.userId(), credentials.getUserId());
		assertEquals(SessionStatus.ACTIVE, session.getStatus());
		assertEquals(
				loggedInAt.plus(securityProperties.sessionIdleLifetime()),
				session.getIdleExpiresAt());
		assertEquals(
				loggedInAt.plus(securityProperties.sessionAbsoluteLifetime()),
				session.getAbsoluteExpiresAt());
		assertEquals(session.getIdleExpiresAt(), token.getExpiresAt());
		assertNotEquals(credentials.getRawRefreshToken(), token.getTokenHash());
		assertEquals(64, token.getTokenHash().length());
		assertFalse(
				credentials.toString().contains(
						credentials.getRawRefreshToken()));
		assertFalse(session.toString().contains("synthetic-laptop-device-id"));
	}

	@Test
	void unknownAndUnverifiedLoginsCreateNoSession() {
		PendingRegistrationResult pending = registrationService.register(
				uniqueEmail("pending-session"),
				PASSWORD,
				"Synthetic",
				"Pending",
				null,
				REGISTERED_AT);
		long sessionsBefore = sessionRepository.count();

		assertTrue(sessionService.login(
				"unknown-" + UUID.randomUUID() + "@example.com",
				PASSWORD,
				"unknown-device-id",
				"Unknown",
				"Synthetic browser",
				"192.0.2.10",
				REGISTERED_AT.plusSeconds(10))
				.isEmpty());
		assertTrue(sessionService.login(
				userEmail(pending.getUserId()),
				PASSWORD,
				"pending-device-id",
				"Pending",
				"Synthetic browser",
				"192.0.2.11",
				REGISTERED_AT.plusSeconds(11))
				.isEmpty());
		assertEquals(sessionsBefore, sessionRepository.count());
	}

	@Test
	void rotationConsumesTheCurrentTokenAndLinksOneReplacement() {
		VerifiedAccount account = verifiedAccount("rotation-lineage");
		Instant loggedInAt = REGISTERED_AT.plusSeconds(120);
		IssuedSessionCredentials initial = login(
				account,
				"rotation-device-id",
				"Laptop",
				loggedInAt);
		Instant rotatedAt = loggedInAt.plus(Duration.ofHours(2));

		IssuedSessionCredentials replacement = rotationService.rotate(
				initial.getRawRefreshToken(),
				"192.0.2.20",
				"Updated synthetic browser",
				rotatedAt);

		assertEquals(initial.getSessionId(), replacement.getSessionId());
		assertNotEquals(
				initial.getRawRefreshToken(),
				replacement.getRawRefreshToken());
		assertEquals(
				rotatedAt.plus(securityProperties.sessionIdleLifetime()),
				replacement.getIdleExpiresAt());
		assertEquals(
				2,
				tokenRepository.findAllBySession_IdOrderByCreatedAtAsc(
						initial.getSessionId())
						.size());

		var lineage = jdbcTemplate.queryForMap(
				"""
				SELECT used_at, revocation_reason, replaced_by_token_id
				FROM refresh_token
				WHERE token_hash = ?
				""",
				tokenHashingService.hash(initial.getRawRefreshToken()));
		assertNotNull(lineage.get("used_at"));
		assertEquals("ROTATED", lineage.get("revocation_reason"));
		assertNotNull(lineage.get("replaced_by_token_id"));
		assertEquals(
				rotatedAt,
				jdbcTemplate.queryForObject(
						"SELECT last_activity_at FROM user_session WHERE id = ?",
						Instant.class,
						initial.getSessionId()));
	}

	@Test
	void replayingARotatedTokenCommitsSessionCompromiseAndRevokesReplacement() {
		VerifiedAccount account = verifiedAccount("rotation-replay");
		IssuedSessionCredentials initial = login(
				account,
				"replay-device-id",
				"Laptop",
				REGISTERED_AT.plusSeconds(120));
		Instant firstRotation = REGISTERED_AT.plusSeconds(3_600);
		IssuedSessionCredentials replacement = rotationService.rotate(
				initial.getRawRefreshToken(),
				"192.0.2.30",
				"Synthetic browser",
				firstRotation);

		assertThrows(
				InvalidRefreshTokenException.class,
				() -> rotationService.rotate(
						initial.getRawRefreshToken(),
						"192.0.2.31",
						"Replayed synthetic browser",
						firstRotation.plusSeconds(1)));

		assertEquals(
				SessionStatus.COMPROMISED.name(),
				sessionStatus(initial.getSessionId()));
		assertEquals(
				"REFRESH_TOKEN_REUSE",
				sessionReason(initial.getSessionId()));
		assertEquals(
				"REFRESH_TOKEN_REUSE",
				jdbcTemplate.queryForObject(
						"""
						SELECT revocation_reason
						FROM refresh_token
						WHERE token_hash = ?
						""",
						String.class,
						tokenHashingService.hash(
								replacement.getRawRefreshToken())));
	}

	@Test
	void concurrentRefreshAllowsOneRotationThenCompromisesTheFamily()
			throws Exception {
		VerifiedAccount account = verifiedAccount("concurrent-rotation");
		IssuedSessionCredentials initial = login(
				account,
				"concurrent-device-id",
				"Laptop",
				REGISTERED_AT.plusSeconds(120));
		Instant rotatedAt = REGISTERED_AT.plusSeconds(7_200);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		Callable<Boolean> refresh = () -> {
			ready.countDown();
			if (!start.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("concurrent refresh did not start");
			}
			try {
				rotationService.rotate(
						initial.getRawRefreshToken(),
						"192.0.2.40",
						"Concurrent synthetic browser",
						rotatedAt);
				return true;
			}
			catch (InvalidRefreshTokenException rejected) {
				return false;
			}
		};

		try {
			Future<Boolean> first = executor.submit(refresh);
			Future<Boolean> second = executor.submit(refresh);
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();

			int successes = Boolean.TRUE.equals(await(first)) ? 1 : 0;
			successes += Boolean.TRUE.equals(await(second)) ? 1 : 0;
			assertEquals(1, successes);
		}
		finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}

		assertEquals(
				SessionStatus.COMPROMISED.name(),
				sessionStatus(initial.getSessionId()));
		assertEquals(
				1,
				jdbcTemplate.queryForObject(
						"""
						SELECT COUNT(*)
						FROM refresh_token
						WHERE session_id = ?
						  AND revocation_reason = 'REFRESH_TOKEN_REUSE'
						""",
						Integer.class,
						initial.getSessionId()));
	}

	@Test
	void currentDeviceLogoutDoesNotRevokeAnotherDevice() {
		VerifiedAccount account = verifiedAccount("device-isolation");
		IssuedSessionCredentials laptop = login(
				account,
				"isolated-laptop-id",
				"Laptop",
				REGISTERED_AT.plusSeconds(120));
		IssuedSessionCredentials phone = login(
				account,
				"isolated-phone-id",
				"Phone",
				REGISTERED_AT.plusSeconds(121));

		assertTrue(sessionService.logoutCurrent(
				account.userId(),
				laptop.getSessionId(),
				REGISTERED_AT.plusSeconds(180)));
		assertEquals(
				SessionStatus.REVOKED.name(),
				sessionStatus(laptop.getSessionId()));
		assertEquals(
				SessionStatus.ACTIVE.name(),
				sessionStatus(phone.getSessionId()));
		assertThrows(
				InvalidRefreshTokenException.class,
				() -> rotationService.rotate(
						laptop.getRawRefreshToken(),
						"192.0.2.50",
						"Synthetic browser",
						REGISTERED_AT.plusSeconds(181)));
		assertEquals(
				phone.getSessionId(),
				rotationService.rotate(
						phone.getRawRefreshToken(),
						"192.0.2.51",
						"Synthetic mobile browser",
						REGISTERED_AT.plusSeconds(181))
						.getSessionId());
	}

	@Test
	void globalLogoutRevokesEveryActiveDeviceFamily() {
		VerifiedAccount account = verifiedAccount("global-logout");
		IssuedSessionCredentials laptop = login(
				account,
				"global-laptop-id",
				"Laptop",
				REGISTERED_AT.plusSeconds(120));
		IssuedSessionCredentials phone = login(
				account,
				"global-phone-id",
				"Phone",
				REGISTERED_AT.plusSeconds(121));

		assertEquals(
				2,
				sessionService.logoutEverywhere(
						account.userId(),
						REGISTERED_AT.plusSeconds(180)));
		assertEquals(
				SessionStatus.REVOKED.name(),
				sessionStatus(laptop.getSessionId()));
		assertEquals(
				SessionStatus.REVOKED.name(),
				sessionStatus(phone.getSessionId()));
		assertEquals(
				0,
				jdbcTemplate.queryForObject(
						"""
						SELECT COUNT(*)
						FROM refresh_token
						WHERE user_id = ?
						  AND revoked_at IS NULL
						""",
						Integer.class,
						account.userId()));
	}

	@Test
	void passwordResetAdvancesCredentialsAndRevokesExistingSessions() {
		VerifiedAccount account = verifiedAccount("credential-revocation");
		IssuedSessionCredentials credentials = login(
				account,
				"credential-device-id",
				"Laptop",
				REGISTERED_AT.plusSeconds(120));
		IssuedVerificationToken resetToken =
				verificationService.issuePasswordReset(
						account.userId(),
						REGISTERED_AT.plusSeconds(180));

		verificationService.resetPassword(
				resetToken.getRawToken(),
				NEW_PASSWORD,
				REGISTERED_AT.plusSeconds(240));

		assertEquals(
				SessionStatus.REVOKED.name(),
				sessionStatus(credentials.getSessionId()));
		assertEquals(
				"PASSWORD_RESET",
				sessionReason(credentials.getSessionId()));
		assertThrows(
				InvalidRefreshTokenException.class,
				() -> rotationService.rotate(
						credentials.getRawRefreshToken(),
						"192.0.2.60",
						"Synthetic browser",
						REGISTERED_AT.plusSeconds(241)));
	}

	@Test
	void idleExpirationIsPersistedAndCannotBeRefreshed() {
		VerifiedAccount account = verifiedAccount("idle-expiration");
		Instant loggedInAt = REGISTERED_AT.plusSeconds(120);
		IssuedSessionCredentials credentials = login(
				account,
				"expired-device-id",
				"Laptop",
				loggedInAt);

		assertThrows(
				InvalidRefreshTokenException.class,
				() -> rotationService.rotate(
						credentials.getRawRefreshToken(),
						"192.0.2.70",
						"Synthetic browser",
						credentials.getIdleExpiresAt()));

		assertEquals(
				SessionStatus.EXPIRED.name(),
				sessionStatus(credentials.getSessionId()));
		assertEquals(
				"SESSION_EXPIRED",
				jdbcTemplate.queryForObject(
						"""
						SELECT revocation_reason
						FROM refresh_token
						WHERE token_hash = ?
						""",
						String.class,
						tokenHashingService.hash(
								credentials.getRawRefreshToken())));
	}

	private VerifiedAccount verifiedAccount(String label) {
		String email = uniqueEmail(label);
		PendingRegistrationResult registration = registrationService.register(
				email,
				PASSWORD,
				"Synthetic",
				"Session",
				null,
				REGISTERED_AT);
		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				REGISTERED_AT.plusSeconds(1));
		return new VerifiedAccount(
				registration.getUserId(),
				email,
				PASSWORD);
	}

	private IssuedSessionCredentials login(
			VerifiedAccount account,
			String deviceId,
			String deviceName,
			Instant loggedInAt) {
		return sessionService.login(
				account.email(),
				account.password(),
				deviceId,
				deviceName,
				"Synthetic browser",
				"192.0.2.10",
				loggedInAt)
				.orElseThrow();
	}

	private String userEmail(UUID userId) {
		return jdbcTemplate.queryForObject(
				"SELECT email FROM user_account WHERE id = ?",
				String.class,
				userId);
	}

	private String sessionStatus(UUID sessionId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM user_session WHERE id = ?",
				String.class,
				sessionId);
	}

	private String sessionReason(UUID sessionId) {
		return jdbcTemplate.queryForObject(
				"SELECT revocation_reason FROM user_session WHERE id = ?",
				String.class,
				sessionId);
	}

	private static String uniqueEmail(String label) {
		return label + "-" + UUID.randomUUID() + "@example.com";
	}

	private static Boolean await(Future<Boolean> result)
			throws InterruptedException, ExecutionException,
			java.util.concurrent.TimeoutException {
		return result.get(20, TimeUnit.SECONDS);
	}

	private record VerifiedAccount(
			UUID userId,
			String email,
			String password) {
	}
}
