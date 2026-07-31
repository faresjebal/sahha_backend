package com.sahha.auth.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.jupiter.api.Test;

import com.sahha.auth.service.refreshtokenservice.IssuedRefreshToken;
import com.sahha.auth.service.usersessionservice.IssuedSessionCredentials;

class SessionPersistenceSecurityTests {

	private static final Instant CREATED_AT = Instant.parse("2026-07-01T08:00:00Z");

	@Test
	void sensitiveSessionAndTokenGettersRemainExcludedFromJson() throws Exception {
		assertJsonIgnored(UserSession.class, "getUser");
		assertJsonIgnored(UserSession.class, "getDeviceIdHash");
		assertJsonIgnored(UserSession.class, "getUserAgent");
		assertJsonIgnored(UserSession.class, "getInitialIp");
		assertJsonIgnored(UserSession.class, "getLastIp");
		assertJsonIgnored(UserSession.class, "getRevokedBy");

		assertJsonIgnored(RefreshToken.class, "getSession");
		assertJsonIgnored(RefreshToken.class, "getUser");
		assertJsonIgnored(RefreshToken.class, "getTokenHash");
		assertJsonIgnored(RefreshToken.class, "getParentToken");
		assertJsonIgnored(RefreshToken.class, "getReplacedByToken");
		assertJsonIgnored(RefreshToken.class, "getCreatedIp");
		assertJsonIgnored(RefreshToken.class, "getCreatedUserAgent");
		assertJsonIgnored(IssuedRefreshToken.class, "getRawToken");
		assertJsonIgnored(
				IssuedSessionCredentials.class,
				"getRawRefreshToken");
	}

	@Test
	void stringOutputContainsOnlyExplicitSafeIdentifiersAndState() {
		UserAccount user = pendingAccount();
		UserSession session = session(user);
		RefreshToken token = initialToken(session);

		assertTrue(session.toString().contains(session.getId().toString()));
		assertTrue(session.toString().contains(SessionStatus.ACTIVE.name()));
		assertFalse(session.toString().contains(user.getEmail()));
		assertFalse(session.toString().contains(session.getDeviceIdHash()));
		assertFalse(session.toString().contains(session.getInitialIp()));
		assertFalse(session.toString().contains(session.getUserAgent()));

		assertTrue(token.toString().contains(token.getId().toString()));
		assertFalse(token.toString().contains(token.getTokenHash()));
		assertFalse(token.toString().contains(token.getCreatedIp()));
		assertFalse(token.toString().contains(token.getCreatedUserAgent()));
	}

	@Test
	void persistenceFactoriesAcceptOnlyLowercaseSha256HexHashes() {
		UserAccount user = pendingAccount();

		assertThrows(
				IllegalArgumentException.class,
				() -> UserSession.open(
						user,
						null,
						"raw-device-id",
						"My laptop",
						"Synthetic browser",
						"127.0.0.1",
						CREATED_AT,
						CREATED_AT.plusSeconds(604_800),
						CREATED_AT.plusSeconds(2_592_000)));

		UserSession session = session(user);
		assertThrows(
				IllegalArgumentException.class,
				() -> RefreshToken.issueInitial(
						session,
						"raw-refresh-token",
						CREATED_AT.plusSeconds(1),
						CREATED_AT.plusSeconds(604_800),
						"127.0.0.1",
						"Synthetic browser"));
	}

	private void assertJsonIgnored(Class<?> type, String getterName) throws Exception {
		JsonIgnore annotation = type
				.getMethod(getterName)
				.getAnnotation(JsonIgnore.class);

		assertNotNull(annotation, getterName + " must remain excluded from JSON");
	}

	private UserAccount pendingAccount() {
		return UserAccount.pendingRegistration(
				"session-security@example.com",
				"session-security@example.com",
				"synthetic-password-hash",
				"Synthetic",
				"User",
				null);
	}

	private UserSession session(UserAccount user) {
		return UserSession.open(
				user,
				null,
				"a".repeat(64),
				"My laptop",
				"Synthetic browser",
				"127.0.0.1",
				CREATED_AT,
				CREATED_AT.plusSeconds(604_800),
				CREATED_AT.plusSeconds(2_592_000));
	}

	private RefreshToken initialToken(UserSession session) {
		return RefreshToken.issueInitial(
				session,
				"b".repeat(64),
				CREATED_AT.plusSeconds(1),
				CREATED_AT.plusSeconds(604_800),
				"127.0.0.1",
				"Synthetic browser");
	}
}
