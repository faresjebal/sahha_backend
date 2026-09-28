package com.sahha.file.attachment;
import java.time.Instant;
public record AttachmentUploadTicket(AttachmentResource file,String uploadPath,String uploadToken,Instant expiresAt) {
    @Override public String toString() { return "AttachmentUploadTicket[private]"; }
}
