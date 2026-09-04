package com.sahha.file.service.medicalfiledownloadservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class DownloadGrantTokenCodec {

	private final SecureRandom secureRandom = new SecureRandom();

	IssuedDownloadGrant issue() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new IssuedDownloadGrant(value, digest(value));
	}

	String digestPresented(String value) {
		if (value == null || value.isBlank() || value.length() > 512) {
			return null;
		}
		return digest(value);
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
