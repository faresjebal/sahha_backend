package com.sahha.file.attachment;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import com.sahha.file.config.FileSecurityProperties;
import com.sahha.file.exception.*;
@Component
public class AttachmentConversationClient {
    public record Context(UUID conversationId,UUID organisationId,UUID actorUserId,UUID fileId,
            UUID messageId,UUID messageRequestId,UUID uploaderUserId) { }
    private final RestClient client;
    private final String cookie;
    public AttachmentConversationClient(@Qualifier("fileCommunicationRestClient") RestClient client,FileSecurityProperties properties) {
        this.client=client; this.cookie=properties.accessTokenCookieName();
    }
    public Context require(UUID conversation,UUID org,UUID actor,String token,UUID file) {
        try {
            var context=client.get().uri(uri->{
                uri.path("/api/v1/conversations/{id}/attachment-context");
                if(file!=null) uri.queryParam("fileId",file);
                return uri.build(conversation);
            }).header(HttpHeaders.COOKIE,cookie+"="+token)
                    .headers(headers -> { if(MDC.get("requestId")!=null) headers.set("X-Request-ID",MDC.get("requestId")); })
                    .retrieve().body(Context.class);
            if(context==null || !conversation.equals(context.conversationId()) || !org.equals(context.organisationId())
                    || !actor.equals(context.actorUserId()) || (file!=null && (!file.equals(context.fileId())
                        || context.messageId()==null || context.messageRequestId()==null || context.uploaderUserId()==null)))
                throw new FileResourceNotFoundException();
            return context;
        } catch(HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized denied) {
            throw new FileResourceNotFoundException();
        } catch(RestClientException unavailable) { throw new SharingContextUnavailableException(); }
    }
}
