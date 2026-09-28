package com.sahha.communication.attachment;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import com.sahha.communication.config.CommunicationSecurityProperties;
import com.sahha.communication.exception.*;
@Component
public class AttachmentFileClient {
    public record ReadyFile(UUID fileId,UUID organisationId,UUID conversationId,UUID messageRequestId,
            UUID uploaderUserId,String originalFilename,String contentType,long size,String scanStatus) { }
    private final RestClient client;
    private final String cookie;
    public AttachmentFileClient(@Qualifier("communicationAttachmentFileClient") RestClient client,
            CommunicationSecurityProperties properties) { this.client=client; this.cookie=properties.accessTokenCookieName(); }
    public MessageAttachmentResponse requireReady(UUID file,UUID org,UUID conversation,UUID request,UUID actor,String token) {
        try {
            var value=client.get().uri("/api/v1/files/message-attachments/{id}/send-context",file)
                    .header(HttpHeaders.COOKIE,cookie+"="+token)
                    .headers(headers -> { if(MDC.get("requestId")!=null) headers.set("X-Request-ID",MDC.get("requestId")); })
                    .retrieve().body(ReadyFile.class);
            if(value==null || !file.equals(value.fileId()) || !org.equals(value.organisationId())
                    || !conversation.equals(value.conversationId()) || !request.equals(value.messageRequestId())
                    || !actor.equals(value.uploaderUserId()) || !"CLEAN".equals(value.scanStatus())
                    || value.size()<=0 || value.originalFilename()==null || value.originalFilename().isBlank()
                    || value.originalFilename().length()>255 || value.contentType()==null
                    || !java.util.Set.of("application/pdf","image/png","image/jpeg").contains(value.contentType()))
                throw new ConversationNotFoundException();
            return new MessageAttachmentResponse(file,value.originalFilename(),value.contentType(),value.size());
        } catch(HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized denied) {
            throw new ConversationNotFoundException();
        } catch(HttpClientErrorException.Conflict pending) {
            throw new ConversationConflictException();
        } catch(RestClientException unavailable) { throw new CommunicationContextUnavailableException(); }
    }
}
