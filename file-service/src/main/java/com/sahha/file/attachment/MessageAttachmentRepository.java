package com.sahha.file.attachment;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import com.sahha.file.exception.*;
import com.sahha.file.dto.response.MedicalFileDownloadGrantResponse;

@Repository
public class MessageAttachmentRepository {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public MessageAttachmentRepository(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    StoredMessageAttachment find(UUID file,UUID org) { return find(file,org,false); }
    private StoredMessageAttachment find(UUID file,UUID org,boolean lock) {
        return jdbc.query("SELECT * FROM message_attachment WHERE id=? AND organisation_id=?"+(lock?" FOR UPDATE":""),
                MessageAttachmentRepository::map,file,org).stream().findFirst().orElseThrow(FileResourceNotFoundException::new);
    }
    @Transactional
    public StoredMessageAttachment negotiate(UUID org,UUID actor,AttachmentUploadRequest request,
            String digest,Instant expiry,String requestId) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO message_attachment
                (id,organisation_id,conversation_id,message_request_id,uploader_user_id,upload_request_id,
                 original_filename,content_type,declared_size,expected_checksum,storage_key,upload_status,
                 scan_status,token_digest,token_expires_at,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'NEGOTIATED','PENDING',?,?,?)
                ON CONFLICT (organisation_id,uploader_user_id,upload_request_id) DO NOTHING
                """,id,org,request.conversationId(),request.messageRequestId(),actor,request.uploadRequestId(),
                request.originalFilename(),request.contentType(),request.declaredSize(),request.checksumSha256(),
                "message-attachments/"+org+"/"+id,digest,Timestamp.from(expiry),Timestamp.from(clock.instant()));
        var row=jdbc.query("""
                SELECT * FROM message_attachment WHERE organisation_id=? AND uploader_user_id=? AND upload_request_id=? FOR UPDATE
                """,MessageAttachmentRepository::map,org,actor,request.uploadRequestId()).getFirst();
        if(!row.conversation().equals(request.conversationId()) || !row.messageRequest().equals(request.messageRequestId())
                || !row.filename().equals(request.originalFilename()) || !row.contentType().equals(request.contentType())
                || row.size()!=request.declaredSize() || !row.checksum().equals(request.checksumSha256()))
            throw new FileUploadConflictException();
        if("NEGOTIATED".equals(row.uploadStatus()) && row.tokenUsed()==null) {
            jdbc.update("UPDATE message_attachment SET token_digest=?,token_expires_at=? WHERE id=?",
                    digest,Timestamp.from(expiry),row.id());
            audit(org,actor,row.id(),"UPLOAD_NEGOTIATED","SUCCEEDED",requestId);
        } else if(!"STORED".equals(row.uploadStatus())) throw new FileUploadConflictException();
        return find(row.id(),org);
    }
    @Transactional
    public StoredMessageAttachment claimUpload(UUID file,UUID org,UUID actor,String digest,String type,long length,String requestId) {
        var row=find(file,org,true);
        if(!row.uploader().equals(actor) || !row.tokenDigest().equals(digest)) throw new FileResourceNotFoundException();
        if(!"NEGOTIATED".equals(row.uploadStatus()) || row.tokenUsed()!=null || !clock.instant().isBefore(row.tokenExpires()))
            throw new FileUploadConflictException();
        if(length!=row.size() || !row.contentType().equals(type)) throw new InvalidFileUploadException();
        jdbc.update("UPDATE message_attachment SET upload_status='UPLOADING',token_used_at=? WHERE id=?",Timestamp.from(clock.instant()),file);
        audit(org,actor,file,"UPLOAD_CLAIMED","SUCCEEDED",requestId);
        return row;
    }
    @Transactional
    public StoredMessageAttachment stored(StoredMessageAttachment row,String requestId) {
        if(jdbc.update("""
                UPDATE message_attachment SET upload_status='STORED',uploaded_at=?
                WHERE id=? AND organisation_id=? AND upload_status='UPLOADING'
                """,Timestamp.from(clock.instant()),row.id(),row.org())!=1) throw new FileUploadConflictException();
        audit(row.org(),row.uploader(),row.id(),"UPLOAD_STORED","SUCCEEDED",requestId);
        return find(row.id(),row.org());
    }
    @Transactional
    public void failed(StoredMessageAttachment row,String requestId) {
        jdbc.update("UPDATE message_attachment SET upload_status='FAILED' WHERE id=? AND organisation_id=? AND upload_status='UPLOADING'",row.id(),row.org());
        audit(row.org(),row.uploader(),row.id(),"UPLOAD_FAILED","FAILED",requestId);
    }
    @Transactional
    public StoredMessageAttachment scan(UUID file,UUID org,UUID actor,boolean clean,String requestId) {
        var row=find(file,org,true);
        if(!row.uploader().equals(actor)) throw new FileResourceNotFoundException();
        String decision=clean?"CLEAN":"REJECTED";
        if(!"STORED".equals(row.uploadStatus())) throw new FileScanConflictException();
        if(decision.equals(row.scanStatus())) return row;
        if(!"PENDING".equals(row.scanStatus())) throw new FileScanConflictException();
        jdbc.update("UPDATE message_attachment SET scan_status=? WHERE id=?",decision,file);
        audit(org,actor,file,"SYNTHETIC_SCAN_"+decision,"SUCCEEDED",requestId);
        return find(file,org);
    }
    @Transactional
    public MedicalFileDownloadGrantResponse grant(UUID file,UUID org,UUID actor,String raw,String digest,Instant expiry,String requestId) {
        if(!find(file,org,true).clean()) throw new FileResourceNotFoundException();
        UUID grant=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO message_attachment_download_grant
                (id,file_id,organisation_id,actor_user_id,token_digest,issued_at,expires_at) VALUES (?,?,?,?,?,?,?)
                """,grant,file,org,actor,digest,Timestamp.from(clock.instant()),Timestamp.from(expiry));
        audit(org,actor,file,"DOWNLOAD_GRANT_ISSUED","SUCCEEDED",requestId);
        return new MedicalFileDownloadGrantResponse(grant,file,"/api/v1/files/message-attachments/"+file+"/content",raw,expiry);
    }
    @Transactional
    public StoredMessageAttachment consume(UUID file,UUID org,UUID actor,String digest,String requestId) {
        var row=find(file,org,true);
        if(!row.clean()) throw new FileResourceNotFoundException();
        if(jdbc.update("""
                UPDATE message_attachment_download_grant SET used_at=?
                WHERE file_id=? AND organisation_id=? AND actor_user_id=? AND token_digest=? AND used_at IS NULL AND expires_at>?
                """,Timestamp.from(clock.instant()),file,org,actor,digest,Timestamp.from(clock.instant()))!=1)
            throw new FileResourceNotFoundException();
        audit(org,actor,file,"DOWNLOAD_AUTHORISED","SUCCEEDED",requestId);
        return row;
    }
    // Called after repository transactions have completed; service orchestration is not transactional.
    @Transactional
    public void accessAudit(UUID org,UUID actor,UUID file,String action,boolean allowed,String requestId) {
        audit(org,actor,file,action,allowed?"SUCCEEDED":"DENIED",requestId);
    }
    private void audit(UUID org,UUID actor,UUID file,String action,String result,String requestId) {
        jdbc.update("""
                INSERT INTO message_attachment_audit
                (id,organisation_id,actor_user_id,file_id,action,result,request_id,occurred_at) VALUES (?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),org,actor,file,action,result,requestId,Timestamp.from(clock.instant()));
    }
    private static StoredMessageAttachment map(ResultSet rs,int index) throws SQLException {
        Timestamp used=rs.getTimestamp("token_used_at");
        return new StoredMessageAttachment(rs.getObject("id",UUID.class),rs.getObject("organisation_id",UUID.class),
                rs.getObject("conversation_id",UUID.class),rs.getObject("message_request_id",UUID.class),rs.getObject("uploader_user_id",UUID.class),
                rs.getString("original_filename"),rs.getString("content_type"),rs.getLong("declared_size"),
                rs.getString("expected_checksum"),rs.getString("storage_key"),rs.getString("upload_status"),rs.getString("scan_status"),
                rs.getString("token_digest"),rs.getTimestamp("token_expires_at").toInstant(),used==null?null:used.toInstant());
    }
}
