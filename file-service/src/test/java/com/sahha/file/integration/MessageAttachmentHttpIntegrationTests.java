package com.sahha.file.integration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.file.attachment.AttachmentConversationClient;
import com.sahha.file.exception.*;
import com.sahha.file.storage.PrivateObjectStorage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.startsWith;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@Import(MessageAttachmentHttpIntegrationTests.TimeConfiguration.class)
class MessageAttachmentHttpIntegrationTests {
    static final String BASE="/api/v1/files/message-attachments", TOKEN="sender.attachment.token",
            PEER="peer.attachment.token", OTHER="other.attachment.token", FOREIGN="foreign.attachment.token",
            ADMIN="admin.attachment.token", CSRF="synthetic-attachment-csrf";
    static final byte[] PDF="%PDF-1.4\nSynthetic attachment only\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    static final JsonMapper JSON=JsonMapper.builder().build();
    final UUID org=UUID.randomUUID(), actor=UUID.randomUUID(), peer=UUID.randomUUID(),
            other=UUID.randomUUID(), conversation=UUID.randomUUID(), command=UUID.randomUUID();
    final Set<UUID> sent=new HashSet<>(), files=new HashSet<>();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PrivateObjectStorage storage;
    @Autowired AdjustableClock time;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean AttachmentConversationClient authority;
    @BeforeEach void setup() {
        time.now=Instant.now();
        jwt(TOKEN,actor,org,"DOCTOR"); jwt(PEER,peer,org,"DOCTOR"); jwt(OTHER,other,org,"DOCTOR");
        jwt(FOREIGN,actor,UUID.randomUUID(),"DOCTOR"); jwt(ADMIN,other,org,"ORGANIZATION_ADMIN");
        when(authority.require(any(),any(),any(),anyString(),nullable(UUID.class))).thenAnswer(call->{
            UUID thread=call.getArgument(0), selectedOrg=call.getArgument(1), user=call.getArgument(2), file=call.getArgument(4);
            if(!thread.equals(conversation) || !selectedOrg.equals(org) || (!user.equals(actor) && !user.equals(peer))
                    || (file!=null && !sent.contains(file))) throw new FileResourceNotFoundException();
            return new AttachmentConversationClient.Context(conversation,org,user,file,
                    file==null?null:UUID.randomUUID(),file==null?null:command,file==null?null:actor);
        });
    }
    @AfterEach void removeOwnedMemoryObjects() { for(UUID file:files) storage.remove("message-attachments/"+org+"/"+file); }
    void jwt(String token,UUID user,UUID organisation,String role) {
        when(decoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg","RS256").subject(user.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).claim("sid",UUID.randomUUID().toString())
                .claim("cv",1).claim("roles",List.of()).claim("org_id",organisation.toString())
                .claim("org_roles",List.of(role)).claim("token_type","access").build());
    }
    Cookie auth(String token) { return new Cookie("SAHHA_ACCESS_TOKEN",token); }
    Map<String,Object> declaration(byte[] bytes) throws Exception {
        return new HashMap<>(Map.of("uploadRequestId",UUID.randomUUID(),"conversationId",conversation,"messageRequestId",command,
                "originalFilename","synthetic.pdf","contentType","application/pdf","declaredSize",bytes.length,
                "checksumSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
    }
    ResultActions postAs(String path,Object body,String token) throws Exception {
        var request=post(path).cookie(auth(token),new Cookie("XSRF-TOKEN",CSRF)).header("X-XSRF-TOKEN",CSRF);
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return mvc.perform(request);
    }
    JsonNode json(ResultActions response) throws Exception { return JSON.readTree(response.andReturn().getResponse().getContentAsString()); }
    JsonNode negotiate(Map<String,Object> request) throws Exception {
        var result=json(postAs(BASE+"/uploads",request,TOKEN).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.file.storageKey").doesNotExist()));
        files.add(UUID.fromString(result.get("file").get("fileId").asText())); return result;
    }
    String id(JsonNode ticket) { return ticket.get("file").get("fileId").asText(); }
    ResultActions upload(JsonNode ticket,byte[] bytes) throws Exception {
        return mvc.perform(put(ticket.get("uploadPath").asText()).cookie(auth(TOKEN),new Cookie("XSRF-TOKEN",CSRF))
                .header("X-XSRF-TOKEN",CSRF).header("X-Upload-Token",ticket.get("uploadToken").asText())
                .contentType(MediaType.APPLICATION_PDF).content(bytes));
    }
    JsonNode stored() throws Exception { var ticket=negotiate(declaration(PDF)); upload(ticket,PDF).andExpect(status().isOk()); return ticket; }
    void clean(JsonNode ticket) throws Exception { postAs(BASE+"/"+id(ticket)+"/synthetic-scan",Map.of("decision","CLEAN"),TOKEN).andExpect(status().isOk()); }
    JsonNode grant(JsonNode ticket,String token) throws Exception { return json(postAs(BASE+"/"+id(ticket)+"/download-grants",null,token).andExpect(status().isOk())); }
    ResultActions bytes(JsonNode grant,String token) throws Exception {
        return mvc.perform(get(grant.get("downloadPath").asText()).cookie(auth(token))
                .header("X-Download-Token",grant.get("downloadToken").asText()));
    }
    @Test void negotiationIsImmutableAndUploadStaysQuarantined() throws Exception {
        var request=declaration(PDF); var ticket=negotiate(request); var replay=negotiate(request);
        assertEquals(id(ticket),id(replay)); assertNotEquals(ticket.get("uploadToken").asText(),replay.get("uploadToken").asText());
        upload(ticket,PDF).andExpect(status().isNotFound());
        upload(replay,PDF).andExpect(status().isOk()).andExpect(jsonPath("$.scanStatus").value("PENDING"));
        upload(replay,PDF).andExpect(status().isConflict());
        mvc.perform(get(BASE+"/"+id(ticket)+"/send-context").cookie(auth(TOKEN))).andExpect(status().isConflict());
        mvc.perform(get(BASE+"/"+id(ticket)).cookie(auth(PEER))).andExpect(status().isNotFound());
        postAs(BASE+"/"+id(ticket)+"/download-grants",null,TOKEN).andExpect(status().isNotFound());
        assertEquals(id(ticket),id(negotiate(request)));
        request.put("messageRequestId",UUID.randomUUID()); postAs(BASE+"/uploads",request,TOKEN).andExpect(status().isConflict());
        assertNotEquals(replay.get("uploadToken").asText(),jdbc.queryForObject(
                "select token_digest from message_attachment where id=?",String.class,UUID.fromString(id(ticket))));
    }
    @Test void onlySentCleanFilesHaveOneUseParticipantDownloads() throws Exception {
        var ticket=stored(); clean(ticket);
        postAs(BASE+"/"+id(ticket)+"/download-grants",null,PEER).andExpect(status().isNotFound());
        sent.add(UUID.fromString(id(ticket))); var grant=grant(ticket,PEER);
        bytes(grant,OTHER).andExpect(status().isNotFound()); bytes(grant,FOREIGN).andExpect(status().isNotFound());
        bytes(grant,PEER).andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Disposition",startsWith("attachment;")));
        bytes(grant,PEER).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/"+id(ticket)).cookie(auth(PEER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").doesNotExist()).andExpect(jsonPath("$.storageKey").doesNotExist());
        assertTrue(jdbc.queryForObject("select count(*) from message_attachment_audit where organisation_id=? and result='DENIED'",Long.class,org)>0);
    }
    @Test void grantsRecheckRevokedAuthorityAndFailClosedOnOutage() throws Exception {
        var ticket=stored(); clean(ticket); UUID file=UUID.fromString(id(ticket)); sent.add(file); var grant=grant(ticket,PEER);
        sent.remove(file); bytes(grant,PEER).andExpect(status().isNotFound());
        assertNull(jdbc.queryForObject("select used_at from message_attachment_download_grant where id=?",Instant.class,
                UUID.fromString(grant.get("grantId").asText())));
        when(authority.require(eq(conversation),eq(org),eq(peer),eq(PEER),eq(file))).thenThrow(new SharingContextUnavailableException());
        bytes(grant,PEER).andExpect(status().isServiceUnavailable());
    }
    @Test void expiredUploadAndDownloadTokensStayDenied() throws Exception {
        var pending=negotiate(declaration(PDF)); time.now=time.now.plusSeconds(301);
        upload(pending,PDF).andExpect(status().isConflict());
        var ticket=stored(); clean(ticket); sent.add(UUID.fromString(id(ticket))); var grant=grant(ticket,PEER);
        time.now=time.now.plusSeconds(121); bytes(grant,PEER).andExpect(status().isNotFound());
    }
    @Test void wrongChecksumAndDisguisedTypeNeverBecomeReadable() throws Exception {
        var request=declaration(PDF); request.put("checksumSha256","0".repeat(64)); var ticket=negotiate(request);
        upload(ticket,PDF).andExpect(status().isBadRequest());
        assertFalse(storage.exists("message-attachments/"+org+"/"+id(ticket)));
        byte[] fake="not-a-real-pdf".getBytes(StandardCharsets.US_ASCII); var disguised=negotiate(declaration(fake));
        upload(disguised,fake).andExpect(status().isBadRequest());
        mvc.perform(get(BASE+"/"+id(disguised)).cookie(auth(TOKEN))).andExpect(jsonPath("$.uploadStatus").value("FAILED"));
    }
    @Test void maliciousNamesOversizedFilesAndForeignConversationsAreDenied() throws Exception {
        var request=declaration(PDF); request.put("originalFilename","../synthetic.pdf");
        postAs(BASE+"/uploads",request,TOKEN).andExpect(status().isBadRequest());
        request=declaration(PDF); request.put("declaredSize",1048577); postAs(BASE+"/uploads",request,TOKEN).andExpect(status().isBadRequest());
        request=declaration(PDF); request.put("conversationId",UUID.randomUUID()); postAs(BASE+"/uploads",request,TOKEN).andExpect(status().isNotFound());
        assertEquals(0,jdbc.queryForObject("select count(*) from message_attachment where organisation_id=?",Integer.class,org));
    }
    @Test void csrfAuthenticationAndAdministrativeDenialsPrecedeUploads() throws Exception {
        mvc.perform(post(BASE+"/uploads").cookie(auth(TOKEN)).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(declaration(PDF)))).andExpect(status().isForbidden());
        postAs(BASE+"/uploads",declaration(PDF),ADMIN).andExpect(status().isForbidden());
        mvc.perform(get(BASE+"/"+UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(authority);
    }
    @Test void rejectedScanCannotBeOverwrittenOrDownloaded() throws Exception {
        var ticket=stored();
        postAs(BASE+"/"+id(ticket)+"/synthetic-scan",Map.of("decision","REJECTED"),TOKEN).andExpect(status().isOk());
        cleanFailure(ticket);
        sent.add(UUID.fromString(id(ticket))); postAs(BASE+"/"+id(ticket)+"/download-grants",null,PEER).andExpect(status().isNotFound());
    }
    void cleanFailure(JsonNode ticket) throws Exception {
        postAs(BASE+"/"+id(ticket)+"/synthetic-scan",Map.of("decision","CLEAN"),TOKEN)
                .andExpect(status().isConflict());
    }
    @Test void databaseRejectsOwnershipMutation() throws Exception {
        var ticket=negotiate(declaration(PDF));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update(
                "update message_attachment set conversation_id=? where id=?",UUID.randomUUID(),UUID.fromString(id(ticket))));
    }
    @Test void databaseRejectsAuditMutation() throws Exception {
        negotiate(declaration(PDF));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update(
                "update message_attachment_audit set result='DENIED' where organisation_id=?",org));
    }
    static class AdjustableClock extends Clock {
        Instant now=Instant.now();
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return now;}
    }
    @TestConfiguration static class TimeConfiguration { @Bean @Primary AdjustableClock attachmentClock(){return new AdjustableClock();} }
}
