package com.sahha.file.attachment;
import java.time.Instant;
import java.util.UUID;
/** Internal persistence snapshot; never serialised by controllers. */
record StoredMessageAttachment(UUID id,UUID org,UUID conversation,UUID messageRequest,UUID uploader,
        String filename,String contentType,long size,String checksum,String storageKey,String uploadStatus,
        String scanStatus,String tokenDigest,Instant tokenExpires,Instant tokenUsed) {
    boolean clean() { return "STORED".equals(uploadStatus) && "CLEAN".equals(scanStatus); }
    @Override public String toString() { return "StoredMessageAttachment[private]"; }
}
