package com.sahha.gateway.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayJwtDecoderTests {

	private static final String KEY_ID = "synthetic-gateway-test-key";
	private static final String ISSUER = "https://auth.synthetic.sahha.test";
	private static final String AUDIENCE = "sahha-api";

	private static HttpServer jwkServer;
	private static RSAKey signingKey;
	private static ReactiveJwtDecoder decoder;

	@BeforeAll
	static void startJwkServer() throws Exception {
		signingKey = rsaKey(KEY_ID);
		jwkServer = HttpServer.create(
				new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
				0);
		jwkServer.createContext(
				"/.well-known/jwks.json",
				exchange -> {
					byte[] body = new JWKSet(signingKey.toPublicJWK())
							.toString()
							.getBytes(StandardCharsets.UTF_8);
					exchange.getResponseHeaders().set(
							"Content-Type",
							"application/json");
					exchange.sendResponseHeaders(200, body.length);
					try (var output = exchange.getResponseBody()) {
						output.write(body);
					}
				});
		jwkServer.start();
		GatewaySecurityProperties properties =
				new GatewaySecurityProperties(
						"SAHHA_ACCESS_TOKEN",
						URI.create(
								"http://127.0.0.1:"
										+ jwkServer.getAddress().getPort()
										+ "/.well-known/jwks.json"),
						ISSUER,
						AUDIENCE,
						List.of("http://localhost:5173"));
		decoder = new GatewaySecurityConfiguration()
				.gatewayJwtDecoder(properties);
	}

	@AfterAll
	static void stopJwkServer() {
		if (jwkServer != null) {
			jwkServer.stop(0);
		}
	}

	@Test
	void acceptsOnlyTheConfiguredRs256SahhaAccessContract()
			throws Exception {
		Jwt jwt = decoder.decode(token(
				signingKey,
				JWSAlgorithm.RS256,
				ISSUER,
				AUDIENCE,
				"access")).block();

		assertEquals(AUDIENCE, jwt.getAudience().getFirst());
		assertEquals(
				"access",
				jwt.getClaimAsString("token_type"));
	}

	@Test
	void rejectsWrongSignatureAlgorithmIssuerAudienceAndTokenType()
			throws Exception {
		RSAKey unrelatedKey = rsaKey(KEY_ID);

		assertThrows(
				JwtException.class,
				() -> decoder.decode(token(
						unrelatedKey,
						JWSAlgorithm.RS256,
						ISSUER,
						AUDIENCE,
						"access")).block());
		assertThrows(
				JwtException.class,
				() -> decoder.decode(token(
						signingKey,
						JWSAlgorithm.RS512,
						ISSUER,
						AUDIENCE,
						"access")).block());
		assertThrows(
				JwtException.class,
				() -> decoder.decode(token(
						signingKey,
						JWSAlgorithm.RS256,
						"https://wrong-issuer.example",
						AUDIENCE,
						"access")).block());
		assertThrows(
				JwtException.class,
				() -> decoder.decode(token(
						signingKey,
						JWSAlgorithm.RS256,
						ISSUER,
						"wrong-audience",
						"access")).block());
		assertThrows(
				JwtException.class,
				() -> decoder.decode(token(
						signingKey,
						JWSAlgorithm.RS256,
						ISSUER,
						AUDIENCE,
						"refresh")).block());
	}

	private static RSAKey rsaKey(String keyId) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
				.privateKey((RSAPrivateKey) pair.getPrivate())
				.keyID(keyId)
				.algorithm(JWSAlgorithm.RS256)
				.build();
	}

	private static String token(
			RSAKey key,
			JWSAlgorithm algorithm,
			String issuer,
			String audience,
			String tokenType)
			throws Exception {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.jwtID(UUID.randomUUID().toString())
				.issuer(issuer)
				.audience(audience)
				.subject(UUID.randomUUID().toString())
				.issueTime(Date.from(now))
				.notBeforeTime(Date.from(now.minusSeconds(1)))
				.expirationTime(Date.from(now.plusSeconds(300)))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of("PLATFORM_ADMIN"))
				.claim("org_roles", List.of())
				.claim("token_type", tokenType)
				.build();
		SignedJWT signed = new SignedJWT(
				new JWSHeader.Builder(algorithm)
						.keyID(key.getKeyID())
						.type(JOSEObjectType.JWT)
						.build(),
				claims);
		signed.sign(new RSASSASigner(key.toPrivateKey()));
		return signed.serialize();
	}
}
