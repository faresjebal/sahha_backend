package com.sahha.file.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.file.client.communication.CommunicationShareAccessClient;
import com.sahha.file.entity.MedicalFile;
import com.sahha.file.exception.*;
import com.sahha.file.repository.*;
import com.sahha.file.storage.PrivateObjectStorage;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedMedicalFileHttpIntegrationTests {
    private static final String TOKEN = "shared.file.recipient";
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final byte[] BYTES = "Synthetic private medical document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private final UUID organisation = UUID.randomUUID(), patient = UUID.randomUUID(),
            uploader = UUID.randomUUID(), recipient = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired MedicalFileRepository files;
    @Autowired FileDownloadGrantRepository grants;
    @Autowired FileAuditEventRepository audits;
    @Autowired PrivateObjectStorage storage;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CommunicationShareAccessClient sharing;
    @MockitoBean Clock clock;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        workspace(organisation, recipient, List.of("DOCTOR"));
        doThrow(new FileResourceNotFoundException()).when(sharing)
                .requireGrant(any(), anyString(), any(), any(), nullable(UUID.class), anyString(), anyString());
    }

    @Test void metadataAndBytesRequireAnExactShareAndAnActorBoundOneTimeToken() throws Exception {
        var file = file(true); allow(file);
        mvc.perform(get(path(file)).cookie(access())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.fileId").value(file.getId().toString()))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.patientId").doesNotExist());
        JsonNode grant = issue(file);
        assertEquals(NOW.plusSeconds(30), Instant.parse(grant.get("expiresAt").asText()));
        String token = grant.get("downloadToken").asText();
        mvc.perform(get(grant.get("downloadPath").asText()).cookie(access()).header("X-Download-Token", token))
                .andExpect(status().isOk()).andExpect(content().bytes(BYTES))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(grant.get("downloadPath").asText()).cookie(access()).header("X-Download-Token", token))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/files/{id}/content", file.getId()).cookie(access()).header("X-Download-Token", token))
                .andExpect(status().isNotFound());
        assertTrue(audits.findAll().stream().anyMatch(event -> file.getId().equals(event.getMedicalFileId())
                && recipient.equals(event.getActorUserId()) && event.getEventType().name().equals("DOWNLOADED")));
    }

    @Test void revocationAfterIssuancePreventsBytesAndLeavesTheTokenUnconsumed() throws Exception {
        var file = file(true); allow(file); var grant = issue(file);
        doThrow(new FileResourceNotFoundException()).when(sharing).requireGrant(eq(patient),
                eq("MEDICAL_DOCUMENT"), eq(file.getId()), eq(uploader), isNull(), eq(TOKEN), anyString());
        mvc.perform(get(grant.get("downloadPath").asText()).cookie(access())
                        .header("X-Download-Token", grant.get("downloadToken").asText()))
                .andExpect(status().isNotFound());
        assertNull(grants.findById(UUID.fromString(grant.get("grantId").asText())).orElseThrow().getUsedAt());
        mvc.perform(get(path(file)).cookie(access())).andExpect(status().isNotFound());
    }

    @Test void ownershipMismatchAndUnrelatedDoctorsAreDeniedBeforeIssuingAnyToken() throws Exception {
        var file = file(true);
        mvc.perform(get("/api/v1/files/shared/{patient}/{file}", UUID.randomUUID(), file.getId()).cookie(access()))
                .andExpect(status().isNotFound());
        workspace(UUID.randomUUID(), recipient, List.of("DOCTOR"));
        mvc.perform(get(path(file)).cookie(access())).andExpect(status().isNotFound());
        verifyNoInteractions(sharing);
        workspace(organisation, recipient, List.of("DOCTOR"));
        mvc.perform(get(path(file)).cookie(access())).andExpect(status().isNotFound());
        verify(sharing).requireGrant(eq(patient), eq("MEDICAL_DOCUMENT"), eq(file.getId()),
                eq(uploader), isNull(), eq(TOKEN), anyString());
    }

    @Test void pendingScanCsrfRoleDenialsAndAuthorityOutageCannotReleaseBytes() throws Exception {
        var file = file(false); allow(file);
        mvc.perform(post(path(file) + "/download-grants").cookie(access()))
                .andExpect(status().isForbidden());
        mvc.perform(post(path(file) + "/download-grants").cookie(access(), csrf()).header("X-XSRF-TOKEN", "shared-file-csrf"))
                .andExpect(status().isConflict());
        doThrow(new SharingContextUnavailableException()).when(sharing).requireGrant(eq(patient),
                eq("MEDICAL_DOCUMENT"), eq(file.getId()), eq(uploader), isNull(), eq(TOKEN), anyString());
        mvc.perform(get(path(file)).cookie(access())).andExpect(status().isServiceUnavailable());
        for (String role : List.of("RECEPTIONIST", "ORGANIZATION_ADMIN", "PLATFORM_ADMIN")) {
            workspace(organisation, recipient, List.of(role));
            mvc.perform(get(path(file)).cookie(access())).andExpect(status().isForbidden());
        }
        mvc.perform(get(path(file))).andExpect(status().isUnauthorized());
    }

    @Test void expiredAndStolenTokensRemainDeniedEvenWhenAnotherShareIsAllowed() throws Exception {
        var file = file(true); allow(file); var grant = issue(file);
        // Model another eligible doctor with a different exact grant: download tokens remain actor-bound.
        workspace(organisation, UUID.randomUUID(), List.of("DOCTOR"));
        mvc.perform(get(grant.get("downloadPath").asText()).cookie(access())
                .header("X-Download-Token", grant.get("downloadToken").asText())).andExpect(status().isNotFound());
        workspace(organisation, recipient, List.of("DOCTOR"));
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));
        // The real adapter rejects expired shares; also test the independent persisted token expiry.
        mvc.perform(get(grant.get("downloadPath").asText()).cookie(access())
                .header("X-Download-Token", grant.get("downloadToken").asText())).andExpect(status().isGone());
    }

    private MedicalFile file(boolean clean) throws Exception {
        var value = MedicalFile.negotiate(UUID.randomUUID(), organisation, UUID.randomUUID(), patient,
                UUID.randomUUID(), uploader, "synthetic-shared.pdf", "application/pdf", BYTES.length,
                null, "shared-test/" + UUID.randomUUID(), "a".repeat(64), NOW.minusSeconds(10), NOW.plusSeconds(300));
        value.consumeUploadTicket(NOW.minusSeconds(9));
        value.markStored(BYTES.length, "b".repeat(64), NOW.minusSeconds(8));
        if (clean) value.markClean(NOW.minusSeconds(7));
        files.saveAndFlush(value);
        storage.put(value.getStorageKey(), new ByteArrayInputStream(BYTES), BYTES.length, "application/pdf");
        return value;
    }
    private void allow(MedicalFile file) {
        doReturn(NOW.plusSeconds(30)).when(sharing).requireGrant(eq(patient), eq("MEDICAL_DOCUMENT"),
                eq(file.getId()), eq(uploader), isNull(), eq(TOKEN), anyString());
    }
    private JsonNode issue(MedicalFile file) throws Exception {
        String body = mvc.perform(post(path(file) + "/download-grants").cookie(access(), csrf())
                .header("X-XSRF-TOKEN", "shared-file-csrf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(body);
    }
    private String path(MedicalFile file) { return "/api/v1/files/shared/" + patient + "/" + file.getId(); }
    private Cookie access() { return new Cookie("SAHHA_ACCESS_TOKEN", TOKEN); }
    private Cookie csrf() { return new Cookie("XSRF-TOKEN", "shared-file-csrf"); }
    private void workspace(UUID org, UUID user, List<String> roles) {
        when(decoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "RS256")
                .subject(user.toString()).issuer("http://localhost:8081").audience(List.of("sahha-api"))
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(300)).claim("sid", UUID.randomUUID().toString())
                .claim("cv", 1).claim("roles", List.of()).claim("org_id", org.toString())
                .claim("org_roles", roles).claim("token_type", "access").build());
    }
}
