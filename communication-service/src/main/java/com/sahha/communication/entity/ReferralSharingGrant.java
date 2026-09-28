package com.sahha.communication.entity;

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

@Entity
@Table(name = "referral_sharing_grant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferralSharingGrant {
	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "referral_id", nullable = false, updatable = false)
	private UUID referralId;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "patient_registration_id", nullable = false, updatable = false)
	private UUID patientRegistrationId;
	@Column(name = "recipient_user_id", nullable = false, updatable = false)
	private UUID recipientUserId;
	@Column(name = "recipient_membership_id", nullable = false, updatable = false)
	private UUID recipientMembershipId;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
	private SharingGrantStatus status;
	@Column(name = "valid_from", nullable = false, updatable = false)
	private Instant validFrom;
	@Column(name = "valid_until", nullable = false, updatable = false)
	private Instant validUntil;
	@Column(name = "revoked_at") private Instant revokedAt;
	@Column(name = "expired_at") private Instant expiredAt;
	@Column(name = "termination_reason", length = 500)
	private String terminationReason;
	@Version @Column(nullable = false)
	private long version;

	public static ReferralSharingGrant activate(ReferralRequest referral, Instant now) {
		if (referral.getStatus() != ReferralStatus.ACTIVE) {
			throw new IllegalArgumentException("referral is not active");
		}
		ReferralSharingGrant value = new ReferralSharingGrant();
		value.id = UUID.randomUUID();
		value.referralId = referral.getId();
		value.organisationId = referral.getOrganisationId();
		value.patientRegistrationId = referral.getPatientRegistrationId();
		value.recipientUserId = referral.getRecipientUserId();
		value.recipientMembershipId = referral.getRecipientMembershipId();
		value.status = SharingGrantStatus.ACTIVE;
		value.validFrom = Objects.requireNonNull(now);
		value.validUntil = referral.getAccessExpiresAt();
		return value;
	}

	public void revoke(Instant now, String reason) {
		if (status != SharingGrantStatus.ACTIVE) return;
		status = SharingGrantStatus.REVOKED;
		revokedAt = Objects.requireNonNull(now);
		terminationReason = Objects.requireNonNull(reason);
	}

	public void expire(Instant now) {
		if (status != SharingGrantStatus.ACTIVE) return;
		status = SharingGrantStatus.EXPIRED;
		expiredAt = Objects.requireNonNull(now);
		terminationReason = "ACCESS_WINDOW_EXPIRED";
	}
}
