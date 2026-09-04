package com.sahha.file.service.medicalfileservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class UploadTicketCodec {

	private final SecureRandom secureRandom = new SecureRandom();

	IssuedUploadTicket issue() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new IssuedUploadTicket(value, digest(value));
	}

	boolean matches(String expectedDigest, String presentedValue) {
		if (expectedDigest == null || presentedValue == null
				|| presentedValue.isBlank() || presentedValue.length() > 512) {
			return false;
		}
		return MessageDigest.isEqual(
				expectedDigest.getBytes(StandardCharsets.US_ASCII),
				digest(presentedValue).getBytes(StandardCharsets.US_ASCII));
	}

	private static String digest(String value) {
		try {
			return java.util.HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}
}
