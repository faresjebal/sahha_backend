package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.repository.SecurityEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountProfileHttpIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired SecurityEventRepository events;
    @Autowired EntityManager entityManager;
    @MockitoBean JwtDecoder decoder;
    private static final Cookie ACCESS = new Cookie("SAHHA_ACCESS_TOKEN", "profile.test.access");
    private static final Cookie CSRF = new Cookie("XSRF-TOKEN", "profile-csrf-test");

    @Test
    void savesOnlyCallerProfileAndAuditsWithoutChangingCredentials() throws Exception {
        UserAccount own = account();
        UserAccount other = account();
        authenticate(own);
        long version = own.getVersion();
        long credentialVersion = own.getCredentialVersion();
        mvc.perform(put("/api/v1/auth/account/profile").cookie(ACCESS, CSRF)
                .header("X-XSRF-TOKEN", CSRF.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content(body(version)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.id").value(own.getId().toString()))
            .andExpect(jsonPath("$.firstName").value("Updated"))
            .andExpect(jsonPath("$.phoneNumber").value("+21620123456"))
            .andExpect(jsonPath("$.version").value(version + 1))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.credentialVersion").doesNotExist());
        entityManager.clear();
        mvc.perform(get("/api/v1/auth/account").cookie(ACCESS)).andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Updated"));
        assertEquals("Synthetic", accounts.findById(other.getId()).orElseThrow().getFirstName());
        assertEquals(credentialVersion, accounts.findById(own.getId()).orElseThrow().getCredentialVersion());
        var audit = events.findAllByUserIdOrderByOccurredAtAsc(own.getId());
        assertEquals(1, audit.size());
        assertEquals(SecurityEventType.ACCOUNT_PROFILE_UPDATED, audit.getFirst().getEventType());
        assertFalse(audit.getFirst().toString().contains("+21620123456"));
    }

    @Test
    void rejectsStaleVersionAndMissingCsrfWithoutUpdatingProfile() throws Exception {
        UserAccount own = account();
        authenticate(own);
        mvc.perform(put("/api/v1/auth/account/profile").cookie(ACCESS)
                .contentType(MediaType.APPLICATION_JSON).content(body(own.getVersion())))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/auth/account/profile").cookie(ACCESS, CSRF)
                .header("X-XSRF-TOKEN", CSRF.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content(body(99))).andExpect(status().isConflict());
        assertEquals("Synthetic", own.getFirstName());
        assertEquals(0, events.findAllByUserIdOrderByOccurredAtAsc(own.getId()).size());
    }

    @Test
    void rejectsAnonymousAndInvalidProfileFields() throws Exception {
        mvc.perform(get("/api/v1/auth/account")).andExpect(status().isUnauthorized());
        UserAccount own = account();
        authenticate(own);
        mvc.perform(put("/api/v1/auth/account/profile").cookie(ACCESS, CSRF)
                .header("X-XSRF-TOKEN", CSRF.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content(body(own.getVersion()).replace("Updated", " ").replace("+21620123456", "invalid")))
            .andExpect(status().isBadRequest());
        assertEquals("Synthetic", own.getFirstName());
    }

    private UserAccount account() {
        String email = "profile-" + UUID.randomUUID() + "@example.test";
        Instant created = Instant.parse("2026-07-31T10:00:00Z");
        UserAccount result = UserAccount.pendingRegistration(email, email,
                "$2a$12$synthetic.password.hash.for.profile.testing",
                "Synthetic", "Profile", null, created);
        result.verifyEmail(created.plusSeconds(60));
        return accounts.saveAndFlush(result);
    }

    private void authenticate(UserAccount account) {
        Instant now = Instant.now();
        when(decoder.decode(ACCESS.getValue())).thenReturn(Jwt.withTokenValue(ACCESS.getValue())
                .header("alg", "RS256").subject(account.getId().toString())
                .issuer("http://localhost:8081").audience(List.of("sahha-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("sid", UUID.randomUUID().toString()).claim("cv", account.getCredentialVersion())
                .claim("roles", List.of()).claim("token_type", "access").build());
    }

    private static String body(long version) {
        return """
                {"firstName":"Updated","lastName":"Profile","phoneNumber":"+21620123456","version":%d}
                """.formatted(version);
    }
}
