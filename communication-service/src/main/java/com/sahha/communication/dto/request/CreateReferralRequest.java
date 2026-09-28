package com.sahha.communication.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import com.sahha.communication.entity.ConsentType;
import com.sahha.communication.entity.ReferralPriority;
import com.sahha.communication.entity.ReferralType;

public record CreateReferralRequest(
		@NotNull UUID referralRequestId,
		@NotNull UUID recipientUserId,
		@NotNull UUID patientRegistrationId,
		@NotBlank @Size(min = 4, max = 1000) String reason,
		@NotNull ReferralPriority priority,
		@Size(max = 4000) String clinicalSummary,
		@NotBlank @Size(min = 4, max = 500) String purpose,
		@NotNull ConsentType consentType,
		@NotBlank @Size(min = 3, max = 255) String consentEvidenceReference,
		@NotNull @PastOrPresent Instant consentRecordedAt,
		@NotNull @Future Instant accessExpiresAt,
		@NotNull @Size(max = 50) List<@Valid ReferralShareItemRequest> selectedItems,
		boolean sendImmediately,
		UUID sourceConsultationId,
		ReferralType referralType) {
	public CreateReferralRequest {
		// Older clients can only request the narrower, selected-resource scope.
		if (referralType == null) referralType = ReferralType.SECOND_OPINION;
	}
}
