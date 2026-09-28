package com.sahha.communication.attachment;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.sahha.communication.entity.ConversationMessage;
@Service
public class MessageAttachmentReferences {
    private final JdbcTemplate jdbc;
    private final AttachmentFileClient files;
    private final AttachmentAuthority authority;
    public MessageAttachmentReferences(JdbcTemplate jdbc,AttachmentFileClient files,AttachmentAuthority authority) {
        this.jdbc=jdbc; this.files=files; this.authority=authority;
    }
    public List<MessageAttachmentResponse> validate(List<UUID> ids,UUID org,UUID conversation,UUID request,UUID actor,String token) {
        if(ids.isEmpty()) return List.of();
        if(ids.size()>5 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size())
            throw new IllegalArgumentException("Use at most five distinct attachments");
        authority.require(conversation,org,actor,token,null);
        return ids.stream().sorted().map(id->files.requireReady(id,org,conversation,request,actor,token)).toList();
    }
    public void requireAccess(UUID conversation,UUID org,UUID actor,String token) {
        authority.require(conversation,org,actor,token,null);
    }
    public void link(ConversationMessage message,List<MessageAttachmentResponse> files) {
        for(var file:files) jdbc.update("""
                INSERT INTO conversation_message_attachment
                (file_id,message_id,organisation_id,original_filename,content_type,size_bytes) VALUES (?,?,?,?,?,?)
                """,file.fileId(),message.getId(),message.getOrganisationId(),file.originalFilename(),file.contentType(),file.size());
    }
    public List<MessageAttachmentResponse> list(UUID message,UUID org) {
        return jdbc.query("""
                SELECT file_id,original_filename,content_type,size_bytes FROM conversation_message_attachment
                WHERE message_id=? AND organisation_id=? ORDER BY file_id
                """,(rs,row)->new MessageAttachmentResponse(rs.getObject("file_id",UUID.class),
                    rs.getString("original_filename"),rs.getString("content_type"),rs.getLong("size_bytes")),message,org);
    }
}
