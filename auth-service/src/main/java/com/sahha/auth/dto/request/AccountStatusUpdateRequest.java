package com.sahha.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AccountStatusUpdateRequest(
		@NotNull
		@Schema(example = "SUSPEND")
		AccountAdministrationAction action) {
}
