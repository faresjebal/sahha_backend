package com.sahha.communication.integration;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.communication.attachment.*;
import com.sahha.communication.client.organisation.*;
import com.sahha.communication.exception.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class MessageAttachmentHttpIntegrationTests {
    static final String TOKEN="sender.attachment.token", PEER="peer.attachment.token",
            OTHER="other.attachment.token", FOREIGN="foreign.attachment.token", ADMIN="admin.attachment.token", CSRF="synthetic-csrf";
    static final JsonMapper JSON=JsonMapper.builder().build();
    final UUID org=UUID.randomUUID(), actor=UUID.randomUUID(), peer=UUID.randomUUID(),
            other=UUID.randomUUID(), actorMember=UUID.randomUUID(), peerMember=UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean OrganisationCollaborationClient directory;
    @MockitoBean AttachmentFileClient files;
    @MockitoBean AttachmentAccessAudit audit; // Existing append-only repository is separately covered; do not commit fixture audits.
    @BeforeEach void setup() {
        jwt(TOKEN,actor,org,"DOCTOR"); jwt(PEER,peer,org,"DOCTOR"); jwt(OTHER,other,org,"DOCTOR");
        jwt(FOREIGN,actor,UUID.randomUUID(),"DOCTOR"); jwt(ADMIN,other,org,"ORGANIZATION_ADMIN");
        when(directory.resolve(eq(org),any(),anyString())).thenAnswer(call->{
            UUID user=call.getArgument(1);
            return new CollaborationDoctorResource(user.equals(actor)?actorMember:peerMember,org,user,"Synthetic doctor",1);
        });
        when(files.requireReady(any(),any(),any(),any(),any(),anyString())).thenThrow(new ConversationNotFoundException());
    }
    void jwt(String token,UUID user,UUID selectedOrg,String role) {
        when(decoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg","RS256").subject(user.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).claim("sid",UUID.randomUUID().toString())
                .claim("cv",1).claim("roles",List.of()).claim("org_id",selectedOrg.toString())
                .claim("org_roles",List.of(role)).claim("token_type","access").build());
    }
    ResultActions postAs(String path,Object body,String token) throws Exception {
        return mvc.perform(post(path).cookie(new Cookie("SAHHA_ACCESS_TOKEN",token),new Cookie("XSRF-TOKEN",CSRF))
                .header("X-XSRF-TOKEN",CSRF).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }
    ResultActions getAs(String path,String token) throws Exception {
        return mvc.perform(get(path).cookie(new Cookie("SAHHA_ACCESS_TOKEN",token)));
    }
    JsonNode json(ResultActions result) throws Exception { return JSON.readTree(result.andReturn().getResponse().getContentAsString()); }
    UUID conversation() throws Exception {
        return UUID.fromString(json(postAs("/api/v1/conversations",Map.of("conversationRequestId",UUID.randomUUID(),
                "recipientUserId",peer,"subject","Synthetic attachment conversation"),TOKEN).andExpect(status().isCreated())).get("id").asText());
    }
    Map<String,Object> message(UUID request,List<UUID> ids) { return new HashMap<>(Map.of("messageRequestId",request,"body","Synthetic message","attachmentIds",ids)); }
    void ready(UUID file,UUID conversation,UUID request) {
        doReturn(new MessageAttachmentResponse(file,"synthetic.pdf","application/pdf",42))
                .when(files).requireReady(file,org,conversation,request,actor,TOKEN);
    }
    @Test void immutableReferencesAreReturnedToParticipantsAndRetriesPreserveIdentity() throws Exception {
        UUID thread=conversation(), file=UUID.randomUUID(), request=UUID.randomUUID(); ready(file,thread,request);
        String path="/api/v1/conversations/"+thread+"/messages";
        var command=message(request,List.of(file));
        var sent=json(postAs(path,command,TOKEN).andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].fileId").value(file.toString())));
        postAs(path,command,TOKEN).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(sent.get("id").asText()));
        getAs(path,PEER).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].attachments[0].originalFilename").value("synthetic.pdf"));
        getAs("/api/v1/conversations/"+thread+"/attachment-context?fileId="+file,PEER)
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.messageRequestId").value(request.toString()))
                .andExpect(jsonPath("$.uploaderUserId").value(actor.toString())).andExpect(jsonPath("$.patientId").doesNotExist());
        command.put("attachmentIds",List.of()); postAs(path,command,TOKEN).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("select count(*) from conversation_message where organisation_id=?",Integer.class,org));
        String event=jdbc.queryForObject("select payload::text from communication_outbox_event where organisation_id=? and event_type='message.sent.v1'",String.class,org);
        assertFalse(event.contains("synthetic.pdf")); assertFalse(event.contains(file.toString()));
        verify(files,times(1)).requireReady(file,org,thread,request,actor,TOKEN);
    }
    @Test void anUnreadyOrReboundFileCreatesNoMessageOrPartialReferences() throws Exception {
        UUID thread=conversation(), file=UUID.randomUUID(), request=UUID.randomUUID(); ready(file,thread,request);
        String path="/api/v1/conversations/"+thread+"/messages";
        postAs(path,message(request,List.of(file,UUID.randomUUID())),TOKEN).andExpect(status().isNotFound());
        postAs(path,message(UUID.randomUUID(),List.of(file)),TOKEN).andExpect(status().isNotFound());
        assertEquals(0,jdbc.queryForObject("select count(*) from conversation_message where organisation_id=?",Integer.class,org));
        assertEquals(0,jdbc.queryForObject("select count(*) from conversation_message_attachment where organisation_id=?",Integer.class,org));
    }
    @Test void contextDeniesUnrelatedDoctorsOtherOrganisationsAdminsAndUnknownFiles() throws Exception {
        UUID thread=conversation(); String path="/api/v1/conversations/"+thread+"/attachment-context";
        getAs(path,TOKEN).andExpect(status().isOk()).andExpect(jsonPath("$.messageId").isEmpty());
        getAs(path,OTHER).andExpect(status().isNotFound()); getAs(path,FOREIGN).andExpect(status().isNotFound());
        getAs(path,ADMIN).andExpect(status().isForbidden()); getAs(path+"?fileId="+UUID.randomUUID(),PEER).andExpect(status().isNotFound());
        verify(audit).record(org,thread,other,false);
    }
    @Test void originalMembershipAndParticipantRemovalAreRechecked() throws Exception {
        UUID thread=conversation(); String path="/api/v1/conversations/"+thread+"/attachment-context";
        when(directory.resolve(org,peer,TOKEN)).thenReturn(new CollaborationDoctorResource(UUID.randomUUID(),org,peer,"Synthetic doctor",2));
        getAs(path,TOKEN).andExpect(status().isNotFound());
        jdbc.update("update conversation_participant set active=false where conversation_id=? and user_id=?",thread,peer);
        getAs(path,PEER).andExpect(status().isNotFound());
    }
    @Test void duplicatesAndTooManyFilesAreRejected() throws Exception {
        UUID thread=conversation(),file=UUID.randomUUID(); String path="/api/v1/conversations/"+thread+"/messages";
        postAs(path,message(UUID.randomUUID(),List.of(file,file)),TOKEN).andExpect(status().isBadRequest());
        postAs(path,message(UUID.randomUUID(),java.util.stream.IntStream.range(0,6).mapToObj(i->UUID.randomUUID()).toList()),TOKEN)
                .andExpect(status().isBadRequest());
        verifyNoInteractions(files);
    }
    @Test void changedMembershipAlsoHidesFilenamesInHistoryAndSendRetry() throws Exception {
        UUID thread=conversation(),file=UUID.randomUUID(),request=UUID.randomUUID();ready(file,thread,request);
        String path="/api/v1/conversations/"+thread+"/messages";var command=message(request,List.of(file));
        postAs(path,command,TOKEN).andExpect(status().isCreated());
        when(directory.resolve(org,actor,PEER)).thenReturn(new CollaborationDoctorResource(UUID.randomUUID(),org,actor,"Synthetic doctor",2));
        getAs(path,PEER).andExpect(status().isNotFound());
        when(directory.resolve(org,peer,TOKEN)).thenReturn(new CollaborationDoctorResource(UUID.randomUUID(),org,peer,"Synthetic doctor",2));
        postAs(path,command,TOKEN).andExpect(status().isNotFound());
    }
    @Test void referenceHistoryIsDatabaseImmutable() throws Exception {
        UUID thread=conversation(),file=UUID.randomUUID(),request=UUID.randomUUID(); ready(file,thread,request);
        postAs("/api/v1/conversations/"+thread+"/messages",message(request,List.of(file)),TOKEN).andExpect(status().isCreated());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update(
                "update conversation_message_attachment set original_filename='changed.pdf' where file_id=?",file));
    }
}
