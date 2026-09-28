package com.sahha.communication.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
		@NotNull UUID messageRequestId,
		@NotBlank @Size(max = 4000) String body,
        @Size(max=5) java.util.List<@NotNull UUID> attachmentIds) {
    public SendMessageRequest {
        attachmentIds=attachmentIds==null ? java.util.List.of() : java.util.List.copyOf(attachmentIds);
    }
    public SendMessageRequest(UUID messageRequestId,String body) { this(messageRequestId,body,java.util.List.of()); }
}
