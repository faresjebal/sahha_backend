package com.sahha.communication.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
		@NotNull UUID messageRequestId,
		@NotBlank @Size(max = 4000) String body) {
}
