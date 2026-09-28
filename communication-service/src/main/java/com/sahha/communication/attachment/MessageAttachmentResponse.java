package com.sahha.communication.attachment;
import java.util.UUID;
public record MessageAttachmentResponse(UUID fileId, String originalFilename, String contentType, long size) { }
