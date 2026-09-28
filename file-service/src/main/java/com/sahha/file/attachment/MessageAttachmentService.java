package com.sahha.file.attachment;
import java.io.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import com.sahha.file.config.FileStorageProperties;
import com.sahha.file.dto.response.MedicalFileDownloadGrantResponse;
import com.sahha.file.exception.*;
import com.sahha.file.service.medicalfileservice.UploadIntegrityInputStream;
import com.sahha.file.service.medicalfiledownloadservice.AuthorizedMedicalFileDownload;
import com.sahha.file.storage.*;

@Service
public class MessageAttachmentService {
    public record ReadyFile(UUID fileId,UUID organisationId,UUID conversationId,UUID messageRequestId,
            UUID uploaderUserId,String originalFilename,String contentType,long size,String scanStatus) { }
    private final MessageAttachmentRepository repository;
    private final AttachmentConversationClient conversations;
    private final AttachmentTokens tokens;
    private final PrivateObjectStorage storage;
    private final FileStorageProperties properties;
    private final Clock clock;
    public MessageAttachmentService(MessageAttachmentRepository repository,AttachmentConversationClient conversations,
            AttachmentTokens tokens,PrivateObjectStorage storage,FileStorageProperties properties,Clock clock) {
        this.repository=repository; this.conversations=conversations; this.tokens=tokens;
        this.storage=storage; this.properties=properties; this.clock=clock;
    }
    public AttachmentUploadTicket negotiate(UUID org,UUID actor,String access,AttachmentUploadRequest request,String requestId) {
        return guard(org,actor,request.uploadRequestId(),"UPLOAD_NEGOTIATE",requestId,()->{
            validate(request);
            conversations.require(request.conversationId(),org,actor,access,null);
            String raw=tokens.issue();
            var row=repository.negotiate(org,actor,request,tokens.digest(raw),
                    clock.instant().plus(properties.uploadTicketTtl()),requestId);
            boolean pending="NEGOTIATED".equals(row.uploadStatus());
            return new AttachmentUploadTicket(AttachmentResource.of(row),
                    pending?"/api/v1/files/message-attachments/"+row.id()+"/content":null,
                    pending?raw:null,pending?row.tokenExpires():null);
        });
    }
    public AttachmentResource metadata(UUID file,UUID org,UUID actor,String access,String requestId) {
        return guard(org,actor,file,"METADATA_READ",requestId,()->{
            var row=repository.find(file,org);
            if(actor.equals(row.uploader())) conversations.require(row.conversation(),org,actor,access,null);
            else requireSent(row,actor,access);
            repository.accessAudit(org,actor,file,"METADATA_READ",true,requestId);
            return AttachmentResource.of(row);
        });
    }
    public ReadyFile ready(UUID file,UUID org,UUID actor,String access,String requestId) {
        return guard(org,actor,file,"SEND_CONTEXT",requestId,()->{
            var row=owner(file,org,actor,access);
            if(!row.clean()) throw new FileUploadConflictException();
            repository.accessAudit(org,actor,file,"SEND_CONTEXT",true,requestId);
            return new ReadyFile(file,org,row.conversation(),row.messageRequest(),row.uploader(),
                    row.filename(),row.contentType(),row.size(),row.scanStatus());
        });
    }
    public AttachmentResource upload(UUID file,UUID org,UUID actor,String access,String ticket,
            String type,long length,InputStream input,String requestId) {
        return guard(org,actor,file,"UPLOAD",requestId,()->{
            owner(file,org,actor,access);
            if(length<=0 || length>properties.maximumObjectSize().toBytes()) throw new InvalidFileUploadException();
            var media=MediaType.parseMediaType(type);
            String canonicalType=(media.getType()+"/"+media.getSubtype()).toLowerCase(Locale.ROOT);
            var row=repository.claimUpload(file,org,actor,tokens.digest(ticket),canonicalType,length,requestId);
            try {
                var source=new PushbackInputStream(input,12);
                byte[] header=source.readNBytes(12);
                if(!hasSignature(canonicalType,header)) throw new InvalidFileUploadException();
                source.unread(header);
                var verified=new UploadIntegrityInputStream(source,row.size());
                storage.put(row.storageKey(),verified,row.size(),row.contentType());
                if(verified.count()!=row.size() || !row.checksum().equals(verified.checksumSha256()))
                    throw new InvalidFileUploadException();
                return AttachmentResource.of(repository.stored(row,requestId));
            } catch(IOException invalid) {
                fail(row,requestId); throw new InvalidFileUploadException();
            } catch(ObjectStorageException unavailable) {
                fail(row,requestId); throw new FileStorageUnavailableException(unavailable);
            } catch(RuntimeException failure) {
                fail(row,requestId); throw failure;
            }
        });
    }
    public AttachmentResource syntheticScan(UUID file,UUID org,UUID actor,String access,boolean clean,String requestId) {
        return guard(org,actor,file,"SYNTHETIC_SCAN",requestId,()->{
            if(!properties.syntheticCleanEnabled()) throw new FileResourceNotFoundException();
            var row=owner(file,org,actor,access);
            if(!"STORED".equals(row.uploadStatus())) throw new FileScanConflictException();
            if(!"PENDING".equals(row.scanStatus()) && !(clean?"CLEAN":"REJECTED").equals(row.scanStatus()))
                throw new FileScanConflictException();
            if(clean && !storage.exists(row.storageKey()))
                throw new FileStorageUnavailableException(new IllegalStateException("Stored attachment unavailable"));
            var updated=repository.scan(file,org,actor,clean,requestId);
            if(!clean) storage.remove(row.storageKey());
            return AttachmentResource.of(updated);
        });
    }
    public MedicalFileDownloadGrantResponse grant(UUID file,UUID org,UUID actor,String access,String requestId) {
        return guard(org,actor,file,"DOWNLOAD_GRANT",requestId,()->{
            var row=repository.find(file,org);
            requireSent(row,actor,access);
            String raw=tokens.issue();
            return repository.grant(file,org,actor,raw,tokens.digest(raw),
                    clock.instant().plus(properties.downloadGrantTtl()),requestId);
        });
    }
    public AuthorizedMedicalFileDownload download(UUID file,UUID org,UUID actor,String access,String token,String requestId) {
        return guard(org,actor,file,"DOWNLOAD",requestId,()->{
            var row=repository.find(file,org);
            // A previously issued token never substitutes for a fresh participant decision.
            requireSent(row,actor,access);
            row=repository.consume(file,org,actor,tokens.digest(token),requestId);
            try { return new AuthorizedMedicalFileDownload(file,row.filename(),row.contentType(),row.size(),storage.get(row.storageKey())); }
            catch(ObjectStorageException unavailable) { throw new FileStorageUnavailableException(unavailable); }
        });
    }
    private StoredMessageAttachment owner(UUID file,UUID org,UUID actor,String access) {
        var row=repository.find(file,org);
        if(!actor.equals(row.uploader())) throw new FileResourceNotFoundException();
        conversations.require(row.conversation(),org,actor,access,null);
        return row;
    }
    private void requireSent(StoredMessageAttachment row,UUID actor,String access) {
        if(!row.clean()) throw new FileResourceNotFoundException();
        var context=conversations.require(row.conversation(),row.org(),actor,access,row.id());
        if(!row.uploader().equals(context.uploaderUserId()) || !row.messageRequest().equals(context.messageRequestId()))
            throw new FileResourceNotFoundException();
    }
    private void fail(StoredMessageAttachment row,String requestId) {
        try { storage.remove(row.storageKey()); } catch(RuntimeException ignored) { /* Remains inaccessible; no unsafe object adoption. */ }
        repository.failed(row,requestId);
    }
    private <T> T guard(UUID org,UUID actor,UUID file,String action,String requestId,Supplier<T> operation) {
        try { return operation.get(); }
        catch(RuntimeException failure) {
            repository.accessAudit(org,actor,file,action,false,requestId);
            throw failure;
        }
    }
    private void validate(AttachmentUploadRequest request) {
        String name=request.originalFilename(), type=request.contentType();
        boolean extension= name!=null && switch(type==null?"":type) {
            case "application/pdf" -> name.toLowerCase(Locale.ROOT).endsWith(".pdf");
            case "image/png" -> name.toLowerCase(Locale.ROOT).endsWith(".png");
            case "image/jpeg" -> name.toLowerCase(Locale.ROOT).endsWith(".jpg") || name.toLowerCase(Locale.ROOT).endsWith(".jpeg");
            default -> false;
        };
        if(request.uploadRequestId()==null || request.conversationId()==null || request.messageRequestId()==null
                || name==null || name.isBlank() || name.length()>255 || !name.equals(name.strip())
                || name.indexOf('/')>=0 || name.indexOf((char)92)>=0 || name.codePoints().anyMatch(Character::isISOControl)
                || !extension || !properties.allows(type) || request.declaredSize()<=0
                || request.declaredSize()>properties.maximumObjectSize().toBytes()
                || request.checksumSha256()==null || !request.checksumSha256().matches("[0-9a-f]{64}"))
            throw new InvalidFileUploadException();
    }
    static boolean hasSignature(String type,byte[] bytes) {
        byte[] signature=switch(type) {
            case "application/pdf" -> new byte[]{37,80,68,70,45};
            case "image/png" -> new byte[]{(byte)137,80,78,71,13,10,26,10};
            case "image/jpeg" -> new byte[]{(byte)255,(byte)216,(byte)255};
            default -> new byte[0];
        };
        return signature.length>0 && bytes.length>=signature.length
                && Arrays.equals(signature,Arrays.copyOf(bytes,signature.length));
    }
}
