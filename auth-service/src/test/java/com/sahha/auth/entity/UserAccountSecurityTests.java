package com.sahha.auth.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.jupiter.api.Test;

class UserAccountSecurityTests {

	@Test
	void passwordHashIsExcludedFromSerializationAndSafeStringOutput() throws Exception {
		UserAccount account = UserAccount.pendingRegistration(
				"doctor@example.com",
				"doctor@example.com",
				"synthetic-password-hash",
				"Synthetic",
				"Doctor",
				null);

		JsonIgnore annotation = UserAccount.class
				.getMethod("getPasswordHash")
				.getAnnotation(JsonIgnore.class);

		assertNotNull(annotation);
		assertFalse(account.toString().contains("synthetic-password-hash"));
		assertFalse(account.toString().contains("doctor@example.com"));
		assertTrue(account.toString().contains(account.getId().toString()));
		assertTrue(account.toString().contains(AccountStatus.PENDING_VERIFICATION.name()));
	}
}
