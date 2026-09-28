package com.sahha.organisation.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sahha.session.SessionAuthorityClient;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Real resource filter + Nimbus signature verification; only Auth's HTTP authority is a stand-in. */
@SpringBootTest
@AutoConfigureMockMvc
class DirectServiceSessionHttpIntegrationTests {
    private static final String ISSUER = "https://auth.synthetic.sahha.test";
    private static final AtomicInteger authorityStatus = new AtomicInteger(204);
    private static final AtomicInteger checks = new AtomicInteger();
    private static final HttpServer authority;
    private static final RSAKey key;
    static {
        try {
            key = new RSAKeyGenerator(2048).keyID("synthetic-direct-service").generate();
            authority = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            authority.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            authority.createContext("/session-check", exchange -> {
                checks.incrementAndGet();
                exchange.getResponseHeaders().set(SessionAuthorityClient.CHALLENGE_HEADER,
                        exchange.getRequestHeaders().getFirst(SessionAuthorityClient.CHALLENGE_HEADER));
                exchange.sendResponseHeaders(authorityStatus.get(), -1);
                exchange.close();
            });
            authority.start();
        }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + authority.getAddress().getPort();
        registry.add("sahha.organisation.security.jwk-set-uri", () -> base + "/jwks");
        registry.add("sahha.organisation.security.issuer", () -> ISSUER);
        registry.add("AUTH_SESSION_CHECK_URI", () -> base + "/session-check");
    }

    @Autowired MockMvc mvc;

    @AfterAll
    static void stop() { authority.stop(0); }

    @Test
    void directServiceCannotBypassRevocationEvenWithAnUnexpiredSignedCookie() throws Exception {
        Cookie captured = new Cookie("SAHHA_ACCESS_TOKEN", token(ISSUER));
        authorityStatus.set(204);
        mvc.perform(get("/api/v1/organisations/memberships").cookie(captured)).andExpect(status().isOk());
        try {
            authorityStatus.set(401);
            mvc.perform(get("/api/v1/organisations/memberships").cookie(captured)).andExpect(status().isUnauthorized());
            authorityStatus.set(503);
            mvc.perform(get("/api/v1/organisations/memberships").cookie(captured)).andExpect(status().isUnauthorized());
        }
        finally { authorityStatus.set(204); }
    }

    @Test
    void directServiceRetainsLocalSignatureClaimsAndRoleBoundaries() throws Exception {
        int before = checks.get();
        mvc.perform(get("/api/v1/organisations/memberships")
                .cookie(new Cookie("SAHHA_ACCESS_TOKEN", token("https://wrong.synthetic.test"))))
                .andExpect(status().isUnauthorized());
        assertEquals(before, checks.get());
        mvc.perform(get("/api/v1/platform/organisations")
                .cookie(new Cookie("SAHHA_ACCESS_TOKEN", token(ISSUER))))
                .andExpect(status().isForbidden());
        assertEquals(before + 1, checks.get());
    }

    private String token(String issuer) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString())
                .subject(UUID.randomUUID().toString()).issuer(issuer).audience("sahha-api")
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("sid", UUID.randomUUID().toString()).claim("cv", 1)
                .claim("roles", List.of()).claim("org_roles", List.of()).claim("token_type", "access").build();
        var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        signed.sign(new RSASSASigner(key));
        return signed.serialize();
    }
}
