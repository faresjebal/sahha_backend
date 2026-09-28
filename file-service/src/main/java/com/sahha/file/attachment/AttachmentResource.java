package com.sahha.file.attachment;
import java.util.UUID;
public record AttachmentResource(UUID fileId,String originalFilename,String contentType,long size,
        String uploadStatus,String scanStatus) {
    static AttachmentResource of(StoredMessageAttachment row) {
        return new AttachmentResource(row.id(),row.filename(),row.contentType(),row.size(),row.uploadStatus(),row.scanStatus());
    }
}
