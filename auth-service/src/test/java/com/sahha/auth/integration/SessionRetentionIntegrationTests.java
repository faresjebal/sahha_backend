package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sahha.auth.repository.RefreshTokenRepository;
import com.sahha.auth.repository.UserSessionRepository;
import com.sahha.auth.service.refreshtokenservice.RefreshTokenRotationService;
import com.sahha.auth.service.useraccountservice.AccountRegistrationService;
import com.sahha.auth.service.useraccountservice.AccountVerificationService;
import com.sahha.auth.service.useraccountservice.PendingRegistrationResult;
import com.sahha.auth.service.usersessionservice.IssuedSessionCredentials;
import com.sahha.auth.service.usersessionservice.UserSessionRetentionService;
import com.sahha.auth.service.usersessionservice.UserSessionService;

@SpringBootTest
class SessionRetentionIntegrationTests {

	private static final String PASSWORD =
			"Synthetic retention passphrase 2026!";

	@Autowired
	private AccountRegistrationService registrationService;

	@Autowired
	private AccountVerificationService verificationService;

	@Autowired
	private UserSessionService sessionService;

	@Autowired
	private RefreshTokenRotationService rotationService;

	@Autowired
	private UserSessionRetentionService retentionService;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private UserSessionRepository sessionRepository;

	@Test
	void purgesOnlyLineagePastAbsoluteExpiryAndRetention() {
		Instant observedAt = Instant.now();
		IssuedSessionCredentials expired = createSession(
				"expired",
				observedAt.minus(Duration.ofDays(50)));
		rotationService.rotate(
				expired.getRawRefreshToken(),
				"192.0.2.20",
				"Synthetic retention browser",
				observedAt.minus(Duration.ofDays(50))
						.plus(Duration.ofMinutes(3)));
		IssuedSessionCredentials current = createSession(
				"current",
				observedAt.minus(Duration.ofMinutes(2)));

		assertEquals(
				2,
				refreshTokenRepository
						.findAllBySession_IdOrderByCreatedAtAsc(
								expired.getSessionId())
						.size());
		assertEquals(
				1,
				refreshTokenRepository
						.findAllBySession_IdOrderByCreatedAtAsc(
								current.getSessionId())
						.size());

		int deleted = retentionService.purgeExpiredRefreshTokenFamilies(
				observedAt);

		assertTrue(deleted >= 2);
		assertTrue(refreshTokenRepository
				.findAllBySession_IdOrderByCreatedAtAsc(
						expired.getSessionId())
				.isEmpty());
		assertEquals(
				1,
				refreshTokenRepository
						.findAllBySession_IdOrderByCreatedAtAsc(
								current.getSessionId())
						.size());
		assertTrue(sessionRepository.existsById(expired.getSessionId()));
		assertTrue(sessionRepository.existsById(current.getSessionId()));
	}

	private IssuedSessionCredentials createSession(
			String label,
			Instant registeredAt) {
		String email = "%s-%s@example.com".formatted(
				label,
				UUID.randomUUID());
		PendingRegistrationResult registration = registrationService.register(
				email,
				PASSWORD,
				"Synthetic",
				"Retention",
				null,
				registeredAt);
		verificationService.verifyEmail(
				registration.getVerificationToken().getRawToken(),
				registeredAt.plus(Duration.ofMinutes(1)));
		return sessionService.login(
				email,
				PASSWORD,
				UUID.randomUUID().toString().replace("-", ""),
				"Retention test",
				"Synthetic retention browser",
				"192.0.2.20",
				registeredAt.plus(Duration.ofMinutes(2)))
				.orElseThrow();
	}
}
