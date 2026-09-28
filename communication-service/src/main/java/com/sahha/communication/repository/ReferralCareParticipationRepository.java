package com.sahha.communication.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.communication.entity.ReferralCareParticipation;
import com.sahha.communication.entity.ReferralSharingGrant;

public interface ReferralCareParticipationRepository extends JpaRepository<ReferralCareParticipation, UUID> {
    @Query("""
            select grant from ReferralCareParticipation care, ReferralSharingGrant grant, ReferralRequest referral
            where care.grantId = grant.id and grant.referralId = referral.id
              and care.doctorUserId = :doctorUserId and care.doctorMembershipId = :membershipId
              and grant.organisationId = :organisationId and referral.organisationId = :organisationId
              and grant.patientRegistrationId = :patientRegistrationId
              and referral.patientRegistrationId = :patientRegistrationId
              and referral.referralType = com.sahha.communication.entity.ReferralType.SHARED_TREATMENT
              and referral.status = com.sahha.communication.entity.ReferralStatus.ACTIVE
              and grant.status = com.sahha.communication.entity.SharingGrantStatus.ACTIVE
              and grant.validFrom <= :now and grant.validUntil > :now and referral.accessExpiresAt > :now
            order by grant.validUntil desc, grant.id
            """)
    List<ReferralSharingGrant> findActiveGrants(
            @Param("organisationId") UUID organisationId,
            @Param("patientRegistrationId") UUID patientRegistrationId,
            @Param("doctorUserId") UUID doctorUserId,
            @Param("membershipId") UUID membershipId,
            @Param("now") Instant now, Pageable pageable);
}
