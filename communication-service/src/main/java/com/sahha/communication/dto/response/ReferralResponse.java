package com.sahha.communication.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sahha.communication.entity.ConsentType;
import com.sahha.communication.entity.ReferralPriority;
import com.sahha.communication.entity.ReferralStatus;
import com.sahha.communication.entity.ReferralType;

public record ReferralResponse(
		UUID id,
		UUID organisationId,
		UUID patientRegistrationId,
		UUID senderUserId,
		String senderDisplayName,
		UUID recipientUserId,
		String recipientDisplayName,
		String reason,
		ReferralPriority priority,
		String clinicalSummary,
		String purpose,
		ConsentType consentType,
		String consentEvidenceReference,
		Instant consentRecordedAt,
		Instant accessExpiresAt,
		ReferralStatus status,
		UUID sharingGrantId,
		Instant createdAt,
		Instant sentAt,
		Instant acceptedAt,
		Instant activeAt,
		Instant rejectedAt,
		Instant completedAt,
		Instant revokedAt,
		Instant expiredAt,
		String decisionReason,
		long version,
		List<ReferralShareItemResponse> selectedItems,
		ReferralType referralType) {
}
