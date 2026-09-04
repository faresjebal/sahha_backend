package com.sahha.clinical.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateConsultationDraftRequest(
		@NotNull @PositiveOrZero Long version,
		@Size(max = 1000) String reasonForConsultation,
		@Size(max = 20000) String draftNotes) {
}
