package com.sahha.auth.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sahha.auth.service.verificationtokenservice.IssuedVerificationToken;
import org.junit.jupiter.api.Test;

class VerificationTokenSecurityTests {

	private static final Instant CREATED_AT = Instant.parse("2026-07-01T08:00:00Z");

	@Test
	void persistenceContainsOnlyTheHashAndExcludesItFromOutput() throws Exception {
		UserAccount user = pendingAccount();
		VerificationToken token = VerificationToken.issue(
				user,
				VerificationTokenPurpose.EMAIL_VERIFICATION,
				"a".repeat(64),
				CREATED_AT,
				CREATED_AT.plusSeconds(3_600));

		JsonIgnore hashAnnotation = VerificationToken.class
				.getMethod("getTokenHash")
				.getAnnotation(JsonIgnore.class);
		JsonIgnore userAnnotation = VerificationToken.class
				.getMethod("getUser")
				.getAnnotation(JsonIgnore.class);

		assertNotNull(hashAnnotation);
		assertNotNull(userAnnotation);
		assertThrows(
				NoSuchFieldException.class,
				() -> VerificationToken.class.getDeclaredField("rawToken"));
		assertTrue(token.toString().contains(token.getId().toString()));
		assertTrue(token.toString().contains(
				VerificationTokenPurpose.EMAIL_VERIFICATION.name()));
		assertFalse(token.toString().contains(token.getTokenHash()));
		assertFalse(token.toString().contains(user.getEmail()));
	}

	@Test
	void issuedRawValueIsExcludedFromJsonAndStringOutput() throws Exception {
		IssuedVerificationToken issued = new IssuedVerificationToken(
				java.util.UUID.randomUUID(),
				"synthetic-raw-token",
				VerificationTokenPurpose.PASSWORD_RESET,
				CREATED_AT.plusSeconds(1_800));

		JsonIgnore rawAnnotation = IssuedVerificationToken.class
				.getMethod("getRawToken")
				.getAnnotation(JsonIgnore.class);

		assertNotNull(rawAnnotation);
		assertFalse(issued.toString().contains("synthetic-raw-token"));
	}

	@Test
	void tokenIsSingleUseAndCannotBeConsumedAtExpiration() {
		VerificationToken token = VerificationToken.issue(
				pendingAccount(),
				VerificationTokenPurpose.EMAIL_VERIFICATION,
				"b".repeat(64),
				CREATED_AT,
				CREATED_AT.plusSeconds(3_600));

		assertThrows(
				IllegalStateException.class,
				() -> token.consume(CREATED_AT.plusSeconds(3_600)));

		token.consume(CREATED_AT.plusSeconds(1));
		assertThrows(
				IllegalStateException.class,
				() -> token.consume(CREATED_AT.plusSeconds(2)));
	}

	private UserAccount pendingAccount() {
		return UserAccount.pendingRegistration(
				"verification-security@example.com",
				"verification-security@example.com",
				"synthetic-password-hash",
				"Synthetic",
				"User",
				null,
				CREATED_AT);
	}
}
