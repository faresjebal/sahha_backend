package com.sahha.auth.config;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.sahha.auth.security.RsaKeyMaterial;
import com.sahha.auth.security.SessionBoundJwtValidator;

@Configuration
@EnableConfigurationProperties({
		AuthJwtProperties.class,
		AuthCookieProperties.class
})
public class AuthJwtConfiguration {

	@Bean
	RsaKeyMaterial rsaKeyMaterial(
			AuthJwtProperties properties,
			SecureRandom secureRandom) {
		try {
			KeyPair pair = properties.hasConfiguredKeyPair()
					? decode(properties)
					: generate(properties, secureRandom);
			RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
			RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
			return new RsaKeyMaterial(
					publicKey,
					privateKey,
					keyId(publicKey));
		}
		catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw new IllegalStateException(
					"Auth JWT key material is invalid",
					exception);
		}
	}

	@Bean
	JwtEncoder jwtEncoder(RsaKeyMaterial keyMaterial) {
		ImmutableJWKSet<SecurityContext> jwkSource =
				new ImmutableJWKSet<>(
						new JWKSet(keyMaterial.signingKey()));
		return new NimbusJwtEncoder(jwkSource);
	}

	@Bean
	JwtDecoder jwtDecoder(
			RsaKeyMaterial keyMaterial,
			AuthJwtProperties properties,
			SessionBoundJwtValidator sessionValidator) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder
				.withPublicKey(keyMaterial.publicKey())
				.build();
		decoder.setJwtValidator(
				sessionValidator.withStandardValidation(properties));
		return decoder;
	}

	private static KeyPair decode(AuthJwtProperties properties)
			throws GeneralSecurityException {
		KeyFactory factory = KeyFactory.getInstance("RSA");
		byte[] privateBytes = Base64.getDecoder().decode(
				properties.privateKeyBase64());
		byte[] publicBytes = Base64.getDecoder().decode(
				properties.publicKeyBase64());
		return new KeyPair(
				factory.generatePublic(new X509EncodedKeySpec(publicBytes)),
				factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes)));
	}

	private static KeyPair generate(
			AuthJwtProperties properties,
			SecureRandom secureRandom)
			throws GeneralSecurityException {
		if (!properties.ephemeralKeyEnabled()) {
			throw new IllegalStateException(
					"ephemeral JWT key generation is disabled");
		}
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048, secureRandom);
		return generator.generateKeyPair();
	}

	private static String keyId(RSAPublicKey publicKey)
			throws GeneralSecurityException {
		byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(publicKey.getEncoded());
		return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(digest);
	}
}
