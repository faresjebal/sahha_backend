package com.sahha.communication.attachment;

import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.communication.config.CommunicationSecurityProperties;
import com.sahha.communication.exception.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AttachmentFileClientTests {
    final UUID file=UUID.randomUUID(), organisation=UUID.randomUUID(), conversation=UUID.randomUUID(),
            request=UUID.randomUUID(), actor=UUID.randomUUID();
    final RestClient.Builder builder=RestClient.builder().baseUrl("http://file.test");
    final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
    final AttachmentFileClient client=new AttachmentFileClient(builder.build(),
            new CommunicationSecurityProperties("DEMO_ACCESS",URI.create("http://auth.test/jwks"),
                    "http://auth.test","sahha-api","DEMO_CSRF","X-XSRF-TOKEN",false));
    Map<String,Object> metadata() {
        return new HashMap<>(Map.of("fileId",file,"organisationId",organisation,"conversationId",conversation,
                "messageRequestId",request,"uploaderUserId",actor,"originalFilename","synthetic.pdf",
                "contentType","application/pdf","size",14,"scanStatus","CLEAN"));
    }
    String route() { return "http://file.test/api/v1/files/message-attachments/"+file+"/send-context"; }
    MessageAttachmentResponse read() { return client.requireReady(file,organisation,conversation,request,actor,"synthetic.token"); }
    void respond(Map<String,Object> value) {
        server.expect(requestTo(route())).andRespond(withSuccess(JsonMapper.builder().build().writeValueAsString(value),MediaType.APPLICATION_JSON));
    }
    @Test void forwardsOnlyCurrentCredentialAndCorrelationAndReturnsMinimalReference() {
        MDC.put("requestId","synthetic-attachment");
        try {
            server.expect(requestTo(route())).andExpect(header("Cookie","DEMO_ACCESS=synthetic.token"))
                    .andExpect(header("X-Request-ID","synthetic-attachment"))
                    .andExpect(headerDoesNotExist("Authorization")).andExpect(headerDoesNotExist("X-XSRF-TOKEN"))
                    .andRespond(withSuccess(JsonMapper.builder().build().writeValueAsString(metadata()),MediaType.APPLICATION_JSON));
            assertEquals(new MessageAttachmentResponse(file,"synthetic.pdf","application/pdf",14),read());server.verify();
        } finally { MDC.clear(); }
    }
    @Test void rejectsEveryMissingOrReboundIdentityAndUncleanFile() {
        for(String field:List.of("fileId","organisationId","conversationId","messageRequestId","uploaderUserId","scanStatus","originalFilename","contentType","size")) {
            for(boolean missing:List.of(false,true)) {
                server.reset();var value=metadata();
                if(missing) value.remove(field);
                else value.put(field,switch(field) {
                    case "scanStatus" -> "PENDING";
                    case "originalFilename" -> "";
                    case "contentType" -> "image/svg+xml";
                    case "size" -> 0;
                    default -> UUID.randomUUID();
                });
                respond(value);
                if(missing && field.equals("size")) assertThrows(CommunicationContextUnavailableException.class,this::read);
                else assertThrows(ConversationNotFoundException.class,this::read);
                server.verify();
            }
        }
    }
    @Test void mapsDenialsPendingAndOutagesWithoutEchoingPrivateBodies() {
        for(HttpStatus status:List.of(HttpStatus.UNAUTHORIZED,HttpStatus.FORBIDDEN,HttpStatus.NOT_FOUND,
                HttpStatus.CONFLICT,HttpStatus.SERVICE_UNAVAILABLE)) {
            server.reset();server.expect(requestTo(route())).andRespond(withStatus(status).body("private downstream").contentType(MediaType.TEXT_PLAIN));
            var failure=assertThrows(RuntimeException.class,this::read);
            assertTrue(status==HttpStatus.CONFLICT?failure instanceof ConversationConflictException:
                    status.is4xxClientError()?failure instanceof ConversationNotFoundException:failure instanceof CommunicationContextUnavailableException);
            assertFalse(failure.toString().contains("private downstream"));server.verify();
        }
    }
    @Test void malformedResponsesCannotAuthorizeSending() {
        for(String body:List.of("null","{}","not-json")) {
            server.reset();server.expect(requestTo(route())).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThrows(RuntimeException.class,this::read);server.verify();
        }
    }
}
