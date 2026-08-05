package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.config.AuthSecurityProperties;
import com.sahha.auth.entity.AccountStatus;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.VerificationToken;
import com.sahha.auth.entity.VerificationTokenPurpose;
import com.sahha.auth.exception.InvalidVerificationTokenException;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.repository.VerificationTokenRepository;
import com.sahha.auth.security.TokenHashingService;
import com.sahha.auth.service.useraccountservice.AccountAuthenticationService;
import com.sahha.auth.service.useraccountservice.AccountRegistrationService;
import com.sahha.auth.service.useraccountservice.AccountVerificationService;
import com.sahha.auth.service.useraccountservice.AuthenticationResult;
import com.sahha.auth.service.useraccountservice.PendingRegistrationResult;
import com.sahha.auth.service.verificationtokenservice.IssuedVerificationToken;

@SpringBootTest
class CredentialSecurityIntegrationTests {

	private static final Instant REGISTERED_AT =
			Instant.parse("2026-07-01T08:00:00Z");
	private static final String INITIAL_PASSWORD =
			"synthetic initial passphrase";
	private static final String NEW_PASSWORD =
			"synthetic replacement passphrase";

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private VerificationTokenRepository tokenRepository;

	@Autowired
	private AccountRegistrationService registrationService;

	@Autowired
	private AccountVerificationService verificationService;

	@Autowired
	private AccountAuthenticationService authenticationService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TokenHashingService tokenHashingService;

	@Autowired
	private AuthSecurityProperties securityProperties;

	@Test
	void migrationCreatesVerificationTokensAtCurrentVersion() {
		String tableName = jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.verification_token')::text",
				String.class);

		assertEquals("verification_token", tableName);
		assertNotNull(flyway.info().current());
		assertEquals("7", flyway.info().current().getVersion().getVersion());
	}

	@Test
	@Transactional
	void registrationPersistsBcryptAndOnlyTheVerificationTokenHash() {
		PendingRegistrationResult registration = register(
				"New.User@Example.com",
				REGISTERED_AT);
		UserAccount account = userRepository.findById(registration.getUserId())
				.orElseThrow();
		IssuedVerificationToken issued = registration.getVerificationToken();
		VerificationToken persistedToken = tokenRepository
				.findByTokenHash(tokenHashingService.hash(issued.getRawToken()))
				.orElseThrow();

		assertEquals("new.user@example.com", account.getNormalizedEmail());
		assertEquals(AccountStatus.PENDING_VERIFICATION, account.getStatus());
		assertNotEquals(INITIAL_PASSWORD, account.getPasswordHash());
		assertTrue(account.getPasswordHash().startsWith("$2"));
		assertTrue(passwordEncoder.matches(INITIAL_PASSWORD, account.getPasswordHash()));
		assertNotEquals(issued.getRawToken(), persistedToken.getTokenHash());
		assertEquals(64, persistedToken.getTokenHash().length());
		assertEquals(account.getId(), persistedToken.getUser().getId());
		assertEquals(
				VerificationTokenPurpose.EMAIL_VERIFICATION,
				persistedToken.getPurpose());
	}

	@Test
	@Transactional
	void emailVerificationActivatesTheAccountAndIsSingleUse() {
		PendingRegistrationResult registration = register(
				"verify-once@example.com",
				REGISTERED_AT);
		String rawToken = registration.getVerificationToken().getRawToken();
		Instant verifiedAt = REGISTERED_AT.plusSeconds(60);

		assertEquals(
				registration.getUserId(),
				verificationService.verifyEmail(rawToken, verifiedAt));

		UserAccount verified = userRepository.findById(registration.getUserId())
				.orElseThrow();
		VerificationToken consumed = tokenRepository
				.findByTokenHash(tokenHashingService.hash(rawToken))
				.orElseThrow();
		assertEquals(AccountStatus.ACTIVE, verified.getStatus());
		assertEquals(verifiedAt, verified.getEmailVerifiedAt());
		assertEquals(verifiedAt, consumed.getUsedAt());

		assertThrows(
				InvalidVerificationTokenException.class,
				() -> verificationService.verifyEmail(
						rawToken,
						verifiedAt.plusSeconds(1)));
	}

	@Test
	@Transactional
	void resendRevokesThePreviousActiveTokenBeforeIssuingAReplacement() {
		PendingRegistrationResult registration = register(
				"replacement-token@example.com",
				REGISTERED_AT);
		IssuedVerificationToken first = registration.getVerificationToken();
		Instant reissuedAt = REGISTERED_AT.plusSeconds(3_600);

		IssuedVerificationToken replacement =
				verificationService.resendEmailVerification(
						registration.getUserId(),
						reissuedAt);
		List<VerificationToken> history = tokenRepository
				.findAllByUser_IdAndPurposeOrderByCreatedAtDesc(
						registration.getUserId(),
						VerificationTokenPurpose.EMAIL_VERIFICATION);

		assertEquals(2, history.size());
		VerificationToken previous = tokenRepository
				.findByTokenHash(tokenHashingService.hash(first.getRawToken()))
				.orElseThrow();
		VerificationToken current = tokenRepository
				.findByTokenHash(tokenHashingService.hash(replacement.getRawToken()))
				.orElseThrow();
		assertEquals(reissuedAt, previous.getRevokedAt());
		assertEquals("REPLACED", previous.getRevocationReason());
		assertTrue(current.isActiveAt(reissuedAt));
		assertThrows(
				InvalidVerificationTokenException.class,
				() -> verificationService.verifyEmail(
						first.getRawToken(),
						reissuedAt.plusSeconds(1)));
	}

	@Test
	@Transactional
	void publicVerificationRequestHonoursCooldownThenIssuesAReplacement() {
		PendingRegistrationResult registration = register(
				"verification-cooldown@example.com",
				REGISTERED_AT);

		assertTrue(verificationService.requestEmailVerification(
				"verification-cooldown@example.com",
				REGISTERED_AT.plusSeconds(30))
				.isEmpty());

		var delivery = verificationService.requestEmailVerification(
				"VERIFICATION-COOLDOWN@example.com",
				REGISTERED_AT.plus(
						securityProperties.emailVerificationRequestCooldown()))
				.orElseThrow();
		assertEquals(
				registration.getUserId(),
				userRepository.findByNormalizedEmail(
						"verification-cooldown@example.com")
						.orElseThrow()
						.getId());
		assertEquals(
				VerificationTokenPurpose.EMAIL_VERIFICATION,
				delivery.getIssuedToken().getPurpose());
		assertEquals(
				2,
				tokenRepository
						.findAllByUser_IdAndPurposeOrderByCreatedAtDesc(
								registration.getUserId(),
								VerificationTokenPurpose.EMAIL_VERIFICATION)
						.size());
	}

	@Test
	@Transactional
	void passwordResetConsumesOneTokenAndAdvancesCredentialVersion() {
		PendingRegistrationResult registration = register(
				"password-reset@example.com",
				REGISTERED_AT);
		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				REGISTERED_AT.plusSeconds(60));
		IssuedVerificationToken resetToken =
				verificationService.issuePasswordReset(
						registration.getUserId(),
						REGISTERED_AT.plusSeconds(120));

		verificationService.resetPassword(
				resetToken.getRawToken(),
				NEW_PASSWORD,
				REGISTERED_AT.plusSeconds(180));

		UserAccount account = userRepository.findById(registration.getUserId())
				.orElseThrow();
		VerificationToken consumed = tokenRepository
				.findByTokenHash(
						tokenHashingService.hash(resetToken.getRawToken()))
				.orElseThrow();
		assertEquals(2, account.getCredentialVersion());
		assertFalse(passwordEncoder.matches(
				INITIAL_PASSWORD,
				account.getPasswordHash()));
		assertTrue(passwordEncoder.matches(NEW_PASSWORD, account.getPasswordHash()));
		assertEquals(REGISTERED_AT.plusSeconds(180), account.getPasswordChangedAt());
		assertEquals(REGISTERED_AT.plusSeconds(180), consumed.getUsedAt());
	}

	@Test
	@Transactional
	void publicPasswordResetRequestIsEnumerationAndAccountStateSafe() {
		PendingRegistrationResult registration = register(
				"public-password-reset@example.com",
				REGISTERED_AT);
		assertTrue(verificationService.requestPasswordReset(
				"unknown-password-reset@example.com",
				REGISTERED_AT.plusSeconds(1))
				.isEmpty());
		assertTrue(verificationService.requestPasswordReset(
				"public-password-reset@example.com",
				REGISTERED_AT.plusSeconds(1))
				.isEmpty());

		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				REGISTERED_AT.plusSeconds(2));
		var delivery = verificationService.requestPasswordReset(
				"PUBLIC-PASSWORD-RESET@example.com",
				REGISTERED_AT.plusSeconds(3))
				.orElseThrow();

		assertEquals(
				VerificationTokenPurpose.PASSWORD_RESET,
				delivery.getIssuedToken().getPurpose());
		assertEquals(
				"public-password-reset@example.com",
				delivery.getRecipientEmail());
	}

	@Test
	@Transactional
	void repeatedWrongPasswordsLockThenTimedExpiryAllowsCorrectLogin() {
		PendingRegistrationResult registration = register(
				"login-lock@example.com",
				REGISTERED_AT);
		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				REGISTERED_AT.plusSeconds(1));
		Instant firstAttempt = REGISTERED_AT.plusSeconds(60);

		for (int attempt = 0;
				attempt < securityProperties.maximumFailedLoginAttempts();
				attempt++) {
			AuthenticationResult denied = authenticationService.authenticate(
					"LOGIN-LOCK@example.com",
					"incorrect synthetic password",
					firstAttempt.plusSeconds(attempt));
			assertFalse(denied.isAuthenticated());
		}

		UserAccount locked = userRepository.findById(registration.getUserId())
				.orElseThrow();
		assertEquals(AccountStatus.LOCKED, locked.getStatus());
		assertNotNull(locked.getLockedUntil());
		assertEquals(
				securityProperties.maximumFailedLoginAttempts(),
				locked.getFailedLoginAttempts());
		assertFalse(authenticationService.authenticate(
				"login-lock@example.com",
				INITIAL_PASSWORD,
				locked.getLockedUntil().minusSeconds(1))
				.isAuthenticated());

		AuthenticationResult authenticated = authenticationService.authenticate(
				"login-lock@example.com",
				INITIAL_PASSWORD,
				locked.getLockedUntil());
		assertTrue(authenticated.isAuthenticated());
		assertEquals(
				registration.getUserId(),
				authenticated.authenticatedUserId().orElseThrow());
		assertEquals(AccountStatus.ACTIVE, locked.getStatus());
		assertEquals(0, locked.getFailedLoginAttempts());
		assertEquals(null, locked.getLockedUntil());
		assertEquals(
				firstAttempt
						.plusSeconds(
								securityProperties.maximumFailedLoginAttempts() - 1L)
						.plus(securityProperties.loginLockDuration()),
				locked.getLastLoginAt());
	}

	@Test
	@Transactional
	void unknownAndPendingAccountsReturnTheSameDeniedDecision() {
		register("pending-login@example.com", REGISTERED_AT);

		AuthenticationResult pending = authenticationService.authenticate(
				"pending-login@example.com",
				INITIAL_PASSWORD,
				REGISTERED_AT.plusSeconds(1));
		AuthenticationResult unknown = authenticationService.authenticate(
				"unknown-login@example.com",
				INITIAL_PASSWORD,
				REGISTERED_AT.plusSeconds(1));

		assertFalse(pending.isAuthenticated());
		assertFalse(unknown.isAuthenticated());
		assertTrue(pending.authenticatedUserId().isEmpty());
		assertTrue(unknown.authenticatedUserId().isEmpty());
	}

	@Test
	@Transactional
	void suspendedAndDisabledAccountsRemainDeniedWithTheCorrectPassword() {
		PendingRegistrationResult registration = register(
				"blocked-status@example.com",
				REGISTERED_AT);
		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				REGISTERED_AT.plusSeconds(1));
		UserAccount account = userRepository.findById(registration.getUserId())
				.orElseThrow();

		account.suspend();
		userRepository.saveAndFlush(account);
		assertFalse(authenticationService.authenticate(
				"blocked-status@example.com",
				INITIAL_PASSWORD,
				REGISTERED_AT.plusSeconds(2))
				.isAuthenticated());

		account.disable();
		userRepository.saveAndFlush(account);
		assertFalse(authenticationService.authenticate(
				"blocked-status@example.com",
				INITIAL_PASSWORD,
				REGISTERED_AT.plusSeconds(3))
				.isAuthenticated());
	}

	@Test
	void malformedPresentedTokensUseTheGenericInvalidTokenFailure() {
		assertThrows(
				InvalidVerificationTokenException.class,
				() -> verificationService.verifyEmail("", REGISTERED_AT));
		assertThrows(
				InvalidVerificationTokenException.class,
				() -> verificationService.verifyEmail(null, REGISTERED_AT));
	}

	@Test
	@Transactional
	void databaseRejectsTwoActiveTokensForOneUserAndPurpose() {
		PendingRegistrationResult registration = register(
				"duplicate-active-verification@example.com",
				REGISTERED_AT);
		UserAccount account = userRepository.findById(registration.getUserId())
				.orElseThrow();
		VerificationToken duplicate = VerificationToken.issue(
				account,
				VerificationTokenPurpose.EMAIL_VERIFICATION,
				"c".repeat(64),
				REGISTERED_AT.plusSeconds(1),
				REGISTERED_AT.plusSeconds(3_601));

		assertThrows(
				DataIntegrityViolationException.class,
				() -> tokenRepository.saveAndFlush(duplicate));
	}

	@Test
	@Transactional
	void databaseRejectsLockedStatusWithoutLockExpiration() {
		PendingRegistrationResult registration = register(
				"invalid-lock-state@example.com",
				REGISTERED_AT);

		assertThrows(
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update(
						"""
						UPDATE user_account
						SET status = 'LOCKED',
						    locked_until = NULL
						WHERE id = ?
						""",
						registration.getUserId()));
	}

	private PendingRegistrationResult register(String email, Instant registeredAt) {
		return registrationService.register(
				email,
				INITIAL_PASSWORD,
				"Synthetic",
				"User",
				"+21600000000",
				registeredAt);
	}
}
