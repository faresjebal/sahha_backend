package com.sahha.patient.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PatientAccountLinkResponse(
		UUID linkId,
		UUID authUserId,
		Instant linkedAt) {
}
