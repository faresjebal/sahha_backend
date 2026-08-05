package com.sahha.patient.service.patientidentifierservice;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.sahha.patient.config.PatientIdentifierProperties;
import com.sahha.patient.dto.request.PatientIdentifierRequest;

@Service
public class PatientIdentifierProtectionService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final byte[] secret;

	public PatientIdentifierProtectionService(
			PatientIdentifierProperties properties) {
		this.secret = properties.hmacSecret().getBytes(StandardCharsets.UTF_8);
	}

	public ProtectedPatientIdentifier protect(PatientIdentifierRequest request) {
		if (request == null) {
			return null;
		}
		String value = normalizeIdentifier(request.value());
		String canonical = request.type().name() + ":"
				+ request.countryCode().toUpperCase(Locale.ROOT) + ":" + value;
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			String fingerprint = HexFormat.of().formatHex(
					mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
			return new ProtectedPatientIdentifier(
					request.type(),
					fingerprint,
					value.substring(Math.max(0, value.length() - 4)),
					request.countryCode().toUpperCase(Locale.ROOT));
		}
		catch (java.security.GeneralSecurityException unavailableAlgorithm) {
			throw new IllegalStateException(
					"patient identifier protection is unavailable",
					unavailableAlgorithm);
		}
	}

	private static String normalizeIdentifier(String value) {
		if (value == null) {
			throw new IllegalArgumentException("identifier value must not be null");
		}
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
				.toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9]", "");
		if (normalized.length() < 4 || normalized.length() > 40) {
			throw new IllegalArgumentException("identifier value is invalid");
		}
		return normalized;
	}
}
