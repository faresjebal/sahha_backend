package com.sahha.auth.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Objects;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

public final class RsaKeyMaterial {

	private final RSAPublicKey publicKey;
	private final RSAKey signingKey;

	public RsaKeyMaterial(
			RSAPublicKey publicKey,
			RSAPrivateKey privateKey,
			String keyId) {
		this.publicKey = Objects.requireNonNull(
				publicKey,
				"publicKey must not be null");
		signingKey = new RSAKey.Builder(
				this.publicKey)
				.privateKey(Objects.requireNonNull(
						privateKey,
						"privateKey must not be null"))
				.keyID(requireText(keyId, "keyId"))
				.algorithm(JWSAlgorithm.RS256)
				.build();
	}

	public RSAPublicKey publicKey() {
		return publicKey;
	}

	public String keyId() {
		return signingKey.getKeyID();
	}

	public RSAKey signingKey() {
		return signingKey;
	}

	public Map<String, Object> publicJwkSet() {
		return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value;
	}
}
