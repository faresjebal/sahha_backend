package com.sahha.file.attachment;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record AttachmentUploadRequest(@NotNull UUID uploadRequestId, @NotNull UUID conversationId,
        @NotNull UUID messageRequestId, @NotBlank @Size(max=255) String originalFilename,
        @NotBlank String contentType, @Positive long declaredSize,
        @NotBlank @Pattern(regexp="^[0-9a-f]{64}$") String checksumSha256) { }
