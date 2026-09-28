package com.sahha.file.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
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
import org.springframework.dao.DataAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.file.client.clinical.*;
import com.sahha.file.client.communication.CommunicationShareAccessClient;
import com.sahha.file.entity.*;
import com.sahha.file.exception.*;
import com.sahha.file.repository.*;
import com.sahha.file.storage.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
class SharedCareMedicalFileHttpIntegrationTests {
    private static final String TOKEN = "shared.care.file", CSRF = "synthetic-care-csrf";
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final byte[] BYTES = "Synthetic shared-care document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private final UUID org = UUID.randomUUID(), patient = UUID.randomUUID(), globalPatient = UUID.randomUUID(),
            consultation = UUID.randomUUID(), uploader = UUID.randomUUID(), actor = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired MedicalFileRepository files;
    @Autowired FileDownloadGrantRepository grants;
    @Autowired FileAuditEventRepository audits;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean ClinicalCareAccessClient clinical;
    @MockitoBean ClinicalAttachmentAccessClient author;
    @MockitoBean CommunicationShareAccessClient selected;
    @MockitoBean PrivateObjectStorage storage;
    @MockitoBean Clock clock;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW); workspace(org, actor, "DOCTOR");
        when(clinical.resolve(any(), any(), any(), any(), anyString(), anyString())).thenThrow(new FileResourceNotFoundException());
        when(author.resolve(any(), any(), any(), anyString())).thenThrow(new FileResourceNotFoundException());
        when(selected.requireGrant(any(), anyString(), any(), any(), nullable(UUID.class), anyString(), anyString()))
                .thenThrow(new FileResourceNotFoundException());
        when(storage.get(anyString())).thenAnswer(invocation -> new ByteArrayInputStream(BYTES));
    }
    @Test void discoveryIsPagedEncounterBoundCleanOnlyAndAuditedWithoutStorageIdentifiers() throws Exception {
        var clean = file(org, patient, globalPatient, consultation, true);
        file(org, patient, globalPatient, consultation, false);
        file(org, patient, UUID.randomUUID(), consultation, true);
        file(org, UUID.randomUUID(), globalPatient, consultation, true);
        file(UUID.randomUUID(), patient, globalPatient, consultation, true);
        file(org, patient, globalPatient, UUID.randomUUID(), true); allow(actor);
        mvc.perform(get(listPath()).cookie(access()).param("size", "1")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fileId").value(clean.getId().toString()))
                .andExpect(jsonPath("$.content[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.content[0].patientId").doesNotExist());
        mvc.perform(get(listPath()).cookie(access()).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        assertEquals(2, audits.findAll().stream().filter(e -> consultation.equals(e.getConsultationId())
                && e.getEventType() == FileAuditEventType.CARE_DOCUMENTS_READ).count());
        verifyNoInteractions(storage, selected, author);
    }
    @Test void metadataAndBytesRecheckLiveContextAndTokensAreShortLivedOneTimeAndScoped() throws Exception {
        var file = file(); allow(actor);
        mvc.perform(get(path(file)).cookie(access())).andExpect(status().isOk())
                .andExpect(jsonPath("$.file.fileId").value(file.getId().toString()))
                .andExpect(jsonPath("$.validUntil").value(NOW.plusSeconds(30).toString()));
        var grant = issue(file);
        assertEquals(NOW.plusSeconds(30), Instant.parse(grant.get("expiresAt").asText()));
        assertEquals(DownloadAccessScope.SHARED_CARE, grants.findById(UUID.fromString(grant.get("grantId").asText())).orElseThrow().getAccessScope());
        bytes(grant, 200); bytes(grant, 409);
        verify(clinical, times(4)).resolve(eq(org), eq(actor), eq(patient), eq(consultation), eq(TOKEN), anyString());
        verify(storage, times(1)).get(file.getStorageKey());
        assertTrue(audits.findAll().stream().anyMatch(e -> file.getId().equals(e.getMedicalFileId()) && e.getEventType() == FileAuditEventType.DOWNLOADED));
    }
    @Test void revocationOutageAndExpiryAfterIssuanceDenyBytesWithoutConsumingTheToken() throws Exception {
        var file = file(); allow(actor); var grant = issue(file);
        for (RuntimeException denial : List.of(new FileResourceNotFoundException(), new ClinicalContextUnavailableException())) {
            doThrow(denial).when(clinical).resolve(eq(org), eq(actor), eq(patient), eq(consultation), eq(TOKEN), anyString());
            int status = denial instanceof ClinicalContextUnavailableException ? 503 : 404;
            bytes(grant, status);
            mvc.perform(get(path(file)).cookie(access())).andExpect(status().is(status));
            mvc.perform(get(listPath()).cookie(access())).andExpect(status().is(status));
        }
        allow(actor); when(clock.instant()).thenReturn(NOW.plusSeconds(30));
        bytes(grant, 404);
        assertNull(grants.findById(UUID.fromString(grant.get("grantId").asText())).orElseThrow().getUsedAt());
        verifyNoInteractions(storage);
        assertTrue(audits.findAll().stream().anyMatch(e -> file.getId().equals(e.getMedicalFileId()) && e.getResult() == FileAuditResult.DENIED));
    }
    @Test void wrongOrganisationPatientAndGlobalPatientNeverReleaseBytesOrMetadata() throws Exception {
        var file = file(); allow(actor);
        mvc.perform(get("/api/v1/files/shared-care/{patient}/{file}", UUID.randomUUID(), file.getId()).cookie(access())).andExpect(status().isNotFound());
        workspace(UUID.randomUUID(), actor, "DOCTOR");
        mvc.perform(get(path(file)).cookie(access())).andExpect(status().isNotFound());
        verifyNoInteractions(clinical);
        workspace(org, actor, "DOCTOR");
        var mismatched = file(org, patient, UUID.randomUUID(), consultation, true);
        mvc.perform(get(path(mismatched)).cookie(access())).andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }
    @Test void unrelatedActorCannotUseCareAndTokensCannotBeStolenByAnotherAllowedDoctor() throws Exception {
        var file = file(); allow(actor); var grant = issue(file); UUID other = UUID.randomUUID();
        workspace(org, other, "DOCTOR"); bytes(grant, 404); allow(other); bytes(grant, 404);
        assertNull(grants.findById(UUID.fromString(grant.get("grantId").asText())).orElseThrow().getUsedAt());
        workspace(org, actor, "DOCTOR"); bytes(grant, 200);
    }
    @Test void tokenScopesCannotBeReusedAcrossSelectedAndCareRoutesEvenWithBothPermissions() throws Exception {
        var file = file(); allow(actor);
        doReturn(NOW.plusSeconds(30)).when(selected).requireGrant(eq(patient), eq("MEDICAL_DOCUMENT"),
                eq(file.getId()), eq(uploader), isNull(), eq(TOKEN), anyString());
        String selectedPath = "/api/v1/files/shared/" + patient + "/" + file.getId();
        var careGrant = issue(file);
        mvc.perform(get(selectedPath + "/content").cookie(access()).header("X-Download-Token", careGrant.get("downloadToken").asText()))
                .andExpect(status().isNotFound());
        var selectedGrant = json(mvc.perform(post(selectedPath + "/download-grants").cookie(access(), csrf()).header("X-XSRF-TOKEN", CSRF))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get(path(file) + "/content").cookie(access()).header("X-Download-Token", selectedGrant.get("downloadToken").asText()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(storage); bytes(careGrant, 200);
    }
    @Test void noUploadAuthorFallbackAndPendingScansStayUnavailable() throws Exception {
        var file = file(); allow(actor);
        mvc.perform(get("/api/v1/files/{id}/content", file.getId()).cookie(access()).header("X-Download-Token", "invalid"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/files/uploads").cookie(access(), csrf()).header("X-XSRF-TOKEN", CSRF)
                .contentType("application/json").content(json(Map.of("consultationId", consultation, "originalFilename", "synthetic.pdf",
                        "contentType", "application/pdf", "declaredSize", BYTES.length)))).andExpect(status().isNotFound());
        var pending = file(org, patient, globalPatient, consultation, false);
        mvc.perform(get(path(pending)).cookie(access())).andExpect(status().isNotFound());
        mvc.perform(post(path(pending) + "/download-grants").cookie(access(), csrf()).header("X-XSRF-TOKEN", CSRF))
                .andExpect(status().isConflict());
        verifyNoInteractions(storage, selected);
    }
    @Test void csrfRolesInvalidPaginationAndMissingAuthenticationFailBeforeAuthority() throws Exception {
        var file = file();
        mvc.perform(post(path(file) + "/download-grants").cookie(access())).andExpect(status().isForbidden());
        for (String role : List.of("RECEPTIONIST", "ORGANIZATION_ADMIN", "PLATFORM_ADMIN")) {
            workspace(org, actor, role);
            mvc.perform(get(listPath()).cookie(access())).andExpect(status().isForbidden());
            mvc.perform(get(path(file)).cookie(access())).andExpect(status().isForbidden());
        }
        mvc.perform(get(listPath())).andExpect(status().isUnauthorized());
        workspace(org, actor, "DOCTOR");
        mvc.perform(get(listPath()).cookie(access()).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(listPath()).cookie(access()).param("size", "51")).andExpect(status().isBadRequest());
        verifyNoInteractions(clinical, storage);
    }
    @Test void persistedTokenExpiryAppliesEvenWithAnIndependentLongerCareGrant() throws Exception {
        var file = file(); allow(actor); var grant = issue(file);
        doReturn(context(actor, NOW.plusSeconds(300))).when(clinical).resolve(eq(org), eq(actor), eq(patient), eq(consultation), eq(TOKEN), anyString());
        when(clock.instant()).thenReturn(NOW.plusSeconds(30)); bytes(grant, 410); verifyNoInteractions(storage);
    }
    @Test void databaseRejectsScopeMutation() throws Exception {
        var file = file(); allow(actor); var grant = issue(file);
        assertThrows(DataAccessException.class, () ->
                jdbc.update("update file_download_grant set access_scope = 'OWN' where id = ?", UUID.fromString(grant.get("grantId").asText())));
    }
    private MedicalFile file() { return file(org, patient, globalPatient, consultation, true); }
    private MedicalFile file(UUID organisation, UUID registration, UUID global, UUID encounter, boolean clean) {
        var value = MedicalFile.negotiate(UUID.randomUUID(), organisation, encounter, registration, global, uploader,
                "synthetic-care.pdf", "application/pdf", BYTES.length, null, "care-test/" + UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", "").repeat(2), NOW.minusSeconds(10), NOW.plusSeconds(300));
        value.consumeUploadTicket(NOW.minusSeconds(9)); value.markStored(BYTES.length, "b".repeat(64), NOW.minusSeconds(8));
        if (clean) value.markClean(NOW.minusSeconds(7));
        return files.saveAndFlush(value);
    }
    private void allow(UUID user) { doReturn(context(user, NOW.plusSeconds(30))).when(clinical)
            .resolve(eq(org), eq(user), eq(patient), eq(consultation), eq(TOKEN), anyString()); }
    private SharedCareAttachmentContextResource context(UUID user, Instant expiry) {
        return new SharedCareAttachmentContextResource(org, patient, globalPatient, consultation, user, "FINALIZED", expiry);
    }
    private JsonNode issue(MedicalFile file) throws Exception {
        return json(mvc.perform(post(path(file) + "/download-grants").cookie(access(), csrf()).header("X-XSRF-TOKEN", CSRF))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString());
    }
    private void bytes(JsonNode grant, int expected) throws Exception {
        var result = mvc.perform(get(grant.get("downloadPath").asText()).cookie(access()).header("X-Download-Token", grant.get("downloadToken").asText()))
                .andExpect(status().is(expected));
        if (expected == 200) result.andExpect(content().bytes(BYTES)).andExpect(header().string("Cache-Control", "no-store"));
    }
    private String path(MedicalFile file) { return "/api/v1/files/shared-care/" + patient + "/" + file.getId(); }
    private String listPath() { return "/api/v1/files/shared-care/" + patient + "/consultations/" + consultation; }
    private JsonNode json(String value) { return JsonMapper.builder().build().readTree(value); }
    private String json(Object value) { return JsonMapper.builder().build().writeValueAsString(value); }
    private Cookie access() { return new Cookie("SAHHA_ACCESS_TOKEN", TOKEN); }
    private Cookie csrf() { return new Cookie("XSRF-TOKEN", CSRF); }
    private void workspace(UUID organisation, UUID user, String role) {
        when(decoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "RS256").subject(user.toString())
                .issuer("http://localhost:8081").audience(List.of("sahha-api")).issuedAt(NOW).expiresAt(NOW.plusSeconds(600))
                .claim("sid", UUID.randomUUID().toString()).claim("cv", 1).claim("roles", List.of())
                .claim("org_id", organisation.toString()).claim("org_roles", List.of(role)).claim("token_type", "access").build());
    }
}
