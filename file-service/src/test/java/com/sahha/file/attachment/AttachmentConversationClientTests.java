package com.sahha.file.attachment;

import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.file.config.FileSecurityProperties;
import com.sahha.file.exception.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AttachmentConversationClientTests {
    final UUID file=UUID.randomUUID(), organisation=UUID.randomUUID(), conversation=UUID.randomUUID(),
            request=UUID.randomUUID(), actor=UUID.randomUUID(), message=UUID.randomUUID(), uploader=UUID.randomUUID();
    final RestClient.Builder builder=RestClient.builder().baseUrl("http://communication.test");
    final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
    final AttachmentConversationClient client=new AttachmentConversationClient(builder.build(),
            new FileSecurityProperties("DEMO_ACCESS",URI.create("http://auth.test/jwks"),
                    "http://auth.test","sahha-api","DEMO_CSRF","X-XSRF-TOKEN",false));
    Map<String,Object> metadata() {
        return new HashMap<>(Map.of("fileId",file,"organisationId",organisation,"conversationId",conversation,
                "messageRequestId",request,"actorUserId",actor,"messageId",message,"uploaderUserId",uploader));
    }
    String route() { return "http://communication.test/api/v1/conversations/"+conversation+"/attachment-context"; }
    AttachmentConversationClient.Context read() { return client.require(conversation,organisation,actor,"synthetic.token",file); }
    @Test void forwardsExactFileCurrentCredentialAndCorrelation() {
        MDC.put("requestId","synthetic-attachment");
        try {
            server.expect(requestTo(route()+"?fileId="+file)).andExpect(header("Cookie","DEMO_ACCESS=synthetic.token"))
                    .andExpect(header("X-Request-ID","synthetic-attachment"))
                    .andExpect(headerDoesNotExist("Authorization")).andExpect(headerDoesNotExist("X-XSRF-TOKEN"))
                    .andRespond(withSuccess(JsonMapper.builder().build().writeValueAsString(metadata()),MediaType.APPLICATION_JSON));
            assertEquals(message,read().messageId());server.verify();
        } finally { MDC.clear(); }
    }
    @Test void negotiationRequiresFreshParticipantContextWithoutInventingSentAuthority() {
        var value=metadata();for(String field:List.of("fileId","messageId","messageRequestId","uploaderUserId")) value.remove(field);
        server.expect(requestTo(route())).andRespond(withSuccess(JsonMapper.builder().build().writeValueAsString(value),MediaType.APPLICATION_JSON));
        assertNull(client.require(conversation,organisation,actor,"synthetic.token",null).fileId());server.verify();
    }
    @Test void missingOrReboundContextCannotAuthorizeBytes() {
        for(String field:List.of("conversationId","organisationId","actorUserId","fileId","messageId","messageRequestId","uploaderUserId")) {
            server.reset();var value=metadata();value.remove(field);
            server.expect(requestTo(route()+"?fileId="+file)).andRespond(withSuccess(JsonMapper.builder().build().writeValueAsString(value),MediaType.APPLICATION_JSON));
            assertThrows(FileResourceNotFoundException.class,this::read);server.verify();
        }
        for(String field:List.of("conversationId","organisationId","actorUserId","fileId")) {
            server.reset();var value=metadata();value.put(field,UUID.randomUUID());
            server.expect(requestTo(route()+"?fileId="+file)).andRespond(withSuccess(JsonMapper.builder().build().writeValueAsString(value),MediaType.APPLICATION_JSON));
            assertThrows(FileResourceNotFoundException.class,this::read);server.verify();
        }
    }
    @Test void denialsOutagesAndMalformedResponsesFailClosed() {
        for(HttpStatus status:List.of(HttpStatus.UNAUTHORIZED,HttpStatus.FORBIDDEN,HttpStatus.NOT_FOUND,HttpStatus.SERVICE_UNAVAILABLE)) {
            server.reset();server.expect(requestTo(route()+"?fileId="+file)).andRespond(withStatus(status).body("private downstream").contentType(MediaType.TEXT_PLAIN));
            var failure=assertThrows(RuntimeException.class,this::read);
            assertTrue(status.is4xxClientError()?failure instanceof FileResourceNotFoundException:failure instanceof SharingContextUnavailableException);
            assertFalse(failure.toString().contains("private downstream"));server.verify();
        }
        for(String body:List.of("null","{}","not-json")) {
            server.reset();server.expect(requestTo(route()+"?fileId="+file)).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThrows(RuntimeException.class,this::read);server.verify();
        }
    }
}
