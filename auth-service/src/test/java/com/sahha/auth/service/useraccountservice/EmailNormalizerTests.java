package com.sahha.auth.service.useraccountservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmailNormalizerTests {

	private final EmailNormalizer normalizer = new EmailNormalizer();

	@Test
	void trimsAndLowercasesEmail() {
		assertEquals("doctor@example.com", normalizer.normalize("  Doctor@Example.COM  "));
	}

	@Test
	void rejectsNullAndBlankEmail() {
		assertThrows(NullPointerException.class, () -> normalizer.normalize(null));
		assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("   "));
	}
}
