package com.sahha.communication.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.sahha.communication.exception.ReferralStateConflictException;
import com.sahha.communication.exception.ReferralVersionConflictException;

@Entity
@Table(name = "referral_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class ReferralRequest {
	private static final Duration MAXIMUM_ACCESS_DURATION = Duration.ofDays(90);

	@Id @Column(nullable = false, updatable = false) @ToString.Include
	private UUID id;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "request_id", nullable = false, updatable = false)
	private UUID requestId;
	@Column(name = "patient_registration_id", nullable = false, updatable = false)
	private UUID patientRegistrationId;
	@Column(name = "source_consultation_id", updatable = false)
	private UUID sourceConsultationId;
	@Enumerated(EnumType.STRING)
	@Column(name = "referral_type", nullable = false, length = 32, updatable = false)
	private ReferralType referralType;
	@Column(name = "sender_user_id", nullable = false, updatable = false)
	private UUID senderUserId;
	@Column(name = "sender_membership_id", nullable = false, updatable = false)
	private UUID senderMembershipId;
	@Column(name = "sender_display_name_snapshot", nullable = false, length = 201, updatable = false)
	private String senderDisplayNameSnapshot;
	@Column(name = "recipient_user_id", nullable = false, updatable = false)
	private UUID recipientUserId;
	@Column(name = "recipient_membership_id", nullable = false, updatable = false)
	private UUID recipientMembershipId;
	@Column(name = "recipient_display_name_snapshot", nullable = false, length = 201, updatable = false)
	private String recipientDisplayNameSnapshot;
	@Column(nullable = false, length = 1000, updatable = false)
	private String reason;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false)
	private ReferralPriority priority;
	@Column(name = "clinical_summary", length = 4000, updatable = false)
	private String clinicalSummary;
	@Column(nullable = false, length = 500, updatable = false)
	private String purpose;
	@Enumerated(EnumType.STRING)
	@Column(name = "consent_type", nullable = false, length = 32, updatable = false)
	private ConsentType consentType;
	@Column(name = "consent_evidence_reference", nullable = false, length = 255, updatable = false)
	private String consentEvidenceReference;
	@Column(name = "consent_recorded_at", nullable = false, updatable = false)
	private Instant consentRecordedAt;
	@Column(name = "access_expires_at", nullable = false, updatable = false)
	private Instant accessExpiresAt;
	@Column(name = "submitted_immediately", nullable = false, updatable = false)
	private boolean submittedImmediately;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
	private ReferralStatus status;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "sent_at") private Instant sentAt;
	@Column(name = "accepted_at") private Instant acceptedAt;
	@Column(name = "active_at") private Instant activeAt;
	@Column(name = "rejected_at") private Instant rejectedAt;
	@Column(name = "completed_at") private Instant completedAt;
	@Column(name = "revoked_at") private Instant revokedAt;
	@Column(name = "expired_at") private Instant expiredAt;
	@Column(name = "last_action_by_user_id", nullable = false)
	private UUID lastActionByUserId;
	@Column(name = "decision_reason", length = 500)
	private String decisionReason;
	@Version @Column(nullable = false)
	private long version;

	public static ReferralRequest create(UUID organisationId, UUID requestId,
			UUID patientRegistrationId, UUID senderUserId, UUID senderMembershipId,
			String senderDisplayName, UUID recipientUserId, UUID recipientMembershipId,
			String recipientDisplayName, String reason, ReferralPriority priority,
			String clinicalSummary, String purpose, ConsentType consentType,
			String consentEvidenceReference, Instant consentRecordedAt,
			Instant accessExpiresAt, boolean sendImmediately, Instant now) {
		ReferralRequest value = new ReferralRequest();
		value.id = UUID.randomUUID();
		value.organisationId = Objects.requireNonNull(organisationId);
		value.requestId = Objects.requireNonNull(requestId);
		value.referralType = ReferralType.SECOND_OPINION;
		value.patientRegistrationId = Objects.requireNonNull(patientRegistrationId);
		value.senderUserId = Objects.requireNonNull(senderUserId);
		value.senderMembershipId = Objects.requireNonNull(senderMembershipId);
		value.senderDisplayNameSnapshot = required(senderDisplayName, 1, 201, "sender");
		value.recipientUserId = Objects.requireNonNull(recipientUserId);
		value.recipientMembershipId = Objects.requireNonNull(recipientMembershipId);
		value.recipientDisplayNameSnapshot = required(recipientDisplayName, 1, 201, "recipient");
		if (senderUserId.equals(recipientUserId)) {
			throw new IllegalArgumentException("recipient must be another doctor");
		}
		value.reason = required(reason, 4, 1000, "reason");
		value.priority = Objects.requireNonNull(priority);
		value.clinicalSummary = optional(clinicalSummary, 4000, "clinical summary");
		value.purpose = required(purpose, 4, 500, "purpose");
		value.consentType = Objects.requireNonNull(consentType);
		value.consentEvidenceReference = required(
				consentEvidenceReference, 3, 255, "consent evidence reference");
		value.consentRecordedAt = Objects.requireNonNull(consentRecordedAt);
		value.accessExpiresAt = Objects.requireNonNull(accessExpiresAt);
		value.createdAt = Objects.requireNonNull(now);
		if (consentRecordedAt.isAfter(now)) {
			throw new IllegalArgumentException("consent cannot be recorded in the future");
		}
		if (!accessExpiresAt.isAfter(now)
				|| accessExpiresAt.isAfter(now.plus(MAXIMUM_ACCESS_DURATION))) {
			throw new IllegalArgumentException("access duration must be positive and at most 90 days");
		}
		value.status = sendImmediately ? ReferralStatus.SENT : ReferralStatus.DRAFT;
		value.submittedImmediately = sendImmediately;
		value.sentAt = sendImmediately ? now : null;
		value.lastActionByUserId = senderUserId;
		return value;
	}

	public static ReferralRequest createWithSource(UUID organisationId, UUID requestId,
			UUID patientRegistrationId, UUID senderUserId, UUID senderMembershipId,
			String senderDisplayName, UUID recipientUserId, UUID recipientMembershipId,
			String recipientDisplayName, String reason, ReferralPriority priority,
			String clinicalSummary, String purpose, ConsentType consentType,
			String consentEvidenceReference, Instant consentRecordedAt,
			Instant accessExpiresAt, boolean sendImmediately, Instant now, UUID sourceConsultationId,
			ReferralType referralType) {
		var value = create(organisationId, requestId, patientRegistrationId, senderUserId,
				senderMembershipId, senderDisplayName, recipientUserId, recipientMembershipId,
				recipientDisplayName, reason, priority, clinicalSummary, purpose, consentType,
				consentEvidenceReference, consentRecordedAt, accessExpiresAt, sendImmediately, now);
		value.sourceConsultationId = sourceConsultationId;
		value.referralType = Objects.requireNonNull(referralType);
		return value;
	}

	public void send(UUID actorUserId, long expectedVersion, Instant now) {
		requireSender(actorUserId); requireVersion(expectedVersion);
		requireState(ReferralStatus.DRAFT); requireNotExpired(now);
		status = ReferralStatus.SENT; sentAt = Objects.requireNonNull(now);
		lastActionByUserId = actorUserId; decisionReason = null;
	}

	public void accept(UUID actorUserId, long expectedVersion, Instant now) {
		requireRecipient(actorUserId); requireVersion(expectedVersion);
		requireState(ReferralStatus.SENT); requireNotExpired(now);
		acceptedAt = Objects.requireNonNull(now);
		status = ReferralStatus.ACCEPTED;
		activeAt = now;
		status = ReferralStatus.ACTIVE;
		lastActionByUserId = actorUserId; decisionReason = null;
	}

	public void reject(UUID actorUserId, long expectedVersion, String reason, Instant now) {
		requireRecipient(actorUserId); requireVersion(expectedVersion);
		requireState(ReferralStatus.SENT);
		status = ReferralStatus.REJECTED; rejectedAt = Objects.requireNonNull(now);
		lastActionByUserId = actorUserId;
		decisionReason = required(reason, 3, 500, "rejection reason");
	}

	public void revoke(UUID actorUserId, long expectedVersion, String reason, Instant now) {
		requireSender(actorUserId); requireVersion(expectedVersion);
		if (status != ReferralStatus.DRAFT && status != ReferralStatus.SENT
				&& status != ReferralStatus.ACTIVE) {
			throw new ReferralStateConflictException();
		}
		status = ReferralStatus.REVOKED; revokedAt = Objects.requireNonNull(now);
		lastActionByUserId = actorUserId;
		decisionReason = required(reason, 3, 500, "revocation reason");
	}

	public void complete(UUID actorUserId, long expectedVersion, Instant now) {
		if (!senderUserId.equals(actorUserId) && !recipientUserId.equals(actorUserId)) {
			throw new ReferralStateConflictException();
		}
		requireVersion(expectedVersion); requireState(ReferralStatus.ACTIVE);
		status = ReferralStatus.COMPLETED; completedAt = Objects.requireNonNull(now);
		lastActionByUserId = actorUserId; decisionReason = null;
	}

	public boolean expire(UUID actorUserId, Instant now) {
		Objects.requireNonNull(now);
		if ((status != ReferralStatus.SENT && status != ReferralStatus.ACTIVE)
				|| accessExpiresAt.isAfter(now)) return false;
		status = ReferralStatus.EXPIRED; expiredAt = now;
		lastActionByUserId = Objects.requireNonNull(actorUserId);
		decisionReason = "ACCESS_WINDOW_EXPIRED";
		return true;
	}

	private void requireSender(UUID actorUserId) {
		if (!senderUserId.equals(actorUserId)) throw new ReferralStateConflictException();
	}
	private void requireRecipient(UUID actorUserId) {
		if (!recipientUserId.equals(actorUserId)) throw new ReferralStateConflictException();
	}
	private void requireVersion(long expectedVersion) {
		if (expectedVersion != version) throw new ReferralVersionConflictException();
	}
	private void requireState(ReferralStatus expected) {
		if (status != expected) throw new ReferralStateConflictException();
	}
	private void requireNotExpired(Instant now) {
		if (!accessExpiresAt.isAfter(Objects.requireNonNull(now))) {
			throw new ReferralStateConflictException();
		}
	}
	private static String required(String value, int minimum, int maximum, String field) {
		if (value == null) throw new IllegalArgumentException(field + " is required");
		String normalized = value.strip().replaceAll("\\s+", " ");
		if (normalized.length() < minimum || normalized.length() > maximum) {
			throw new IllegalArgumentException(field + " is invalid");
		}
		return normalized;
	}
	private static String optional(String value, int maximum, String field) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.strip();
		if (normalized.length() > maximum) throw new IllegalArgumentException(field + " is invalid");
		return normalized;
	}
}
