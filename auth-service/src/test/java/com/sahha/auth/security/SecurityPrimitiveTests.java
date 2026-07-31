package com.sahha.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.sahha.auth.config.AuthSecurityProperties;

class SecurityPrimitiveTests {

	private final AuthSecurityProperties properties = new AuthSecurityProperties(
			4,
			12,
			128,
			5,
			Duration.ofMinutes(15),
			Duration.ofDays(7),
			Duration.ofDays(30),
			Duration.ofHours(24),
			Duration.ofMinutes(30),
			Duration.ofMinutes(30),
			Duration.ofMinutes(2),
			Duration.ofMinutes(2));

	@Test
	void tokenHashingIsDeterministicLowercaseSha256Hex() {
		TokenHashingService hashingService = new TokenHashingService();

		String first = hashingService.hash("synthetic-one-time-token");
		String second = hashingService.hash("synthetic-one-time-token");

		assertEquals(first, second);
		assertEquals(64, first.length());
		assertTrue(first.matches("^[0-9a-f]{64}$"));
		assertNotEquals(
				first,
				hashingService.hash("different-synthetic-one-time-token"));
	}

	@Test
	void secureGeneratorProducesIndependentUrlSafe256BitValues() {
		SecureTokenGenerator generator = new SecureTokenGenerator(new SecureRandom());

		String first = generator.generate();
		String second = generator.generate();

		assertEquals(43, first.length());
		assertTrue(first.matches("^[A-Za-z0-9_-]+$"));
		assertNotEquals(first, second);
	}

	@Test
	void passwordPolicyUsesLengthAndAllowsLongPassphrases() {
		PasswordPolicy policy = new PasswordPolicy(properties);

		policy.validate("correct horse battery staple");
		assertThrows(
				IllegalArgumentException.class,
				() -> policy.validate("short"));
		assertThrows(
				IllegalArgumentException.class,
				() -> policy.validate(" ".repeat(12)));
		assertThrows(
				IllegalArgumentException.class,
				() -> policy.validate("x".repeat(129)));
	}
}
