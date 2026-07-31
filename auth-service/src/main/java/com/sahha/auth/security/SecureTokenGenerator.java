package com.sahha.auth.security;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class SecureTokenGenerator {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom;

	public SecureTokenGenerator(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
	}

	public String generate() {
		byte[] value = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(value);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}
}
