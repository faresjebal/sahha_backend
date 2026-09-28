package com.sahha.communication.entity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Historical participation; current authority ALWAYS depends on its live grant. */
@Entity
@Table(name = "referral_care_participation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferralCareParticipation {
    @Id @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "grant_id", nullable = false, updatable = false)
    private UUID grantId;
    @Column(name = "doctor_user_id", nullable = false, updatable = false)
    private UUID doctorUserId;
    @Column(name = "doctor_membership_id", nullable = false, updatable = false)
    private UUID doctorMembershipId;
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    public static List<ReferralCareParticipation> begin(
            ReferralRequest referral, ReferralSharingGrant grant) {
        Objects.requireNonNull(referral); Objects.requireNonNull(grant);
        if (referral.getReferralType() != ReferralType.SHARED_TREATMENT
                || referral.getStatus() != ReferralStatus.ACTIVE
                || grant.getStatus() != SharingGrantStatus.ACTIVE
                || !referral.getId().equals(grant.getReferralId())
                || !referral.getOrganisationId().equals(grant.getOrganisationId())
                || !referral.getPatientRegistrationId().equals(grant.getPatientRegistrationId())
                || !referral.getRecipientUserId().equals(grant.getRecipientUserId())
                || !referral.getRecipientMembershipId().equals(grant.getRecipientMembershipId())
                || !referral.getAccessExpiresAt().equals(grant.getValidUntil())) {
            throw new IllegalArgumentException("Active shared-treatment grant required");
        }
        return List.of(participant(grant, referral.getSenderUserId(), referral.getSenderMembershipId()),
                participant(grant, referral.getRecipientUserId(), referral.getRecipientMembershipId()));
    }

    private static ReferralCareParticipation participant(
            ReferralSharingGrant grant, UUID userId, UUID membershipId) {
        var value = new ReferralCareParticipation();
        value.id = UUID.randomUUID(); value.grantId = grant.getId();
        value.doctorUserId = userId; value.doctorMembershipId = membershipId;
        value.startedAt = grant.getValidFrom();
        return value;
    }
}
