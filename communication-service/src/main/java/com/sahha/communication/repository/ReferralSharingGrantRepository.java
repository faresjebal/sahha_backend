package com.sahha.communication.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.communication.entity.ReferralSharingGrant;
import com.sahha.communication.entity.ShareResourceType;

public interface ReferralSharingGrantRepository
		extends JpaRepository<ReferralSharingGrant, UUID> {
	Optional<ReferralSharingGrant> findByReferralId(UUID referralId);
	List<ReferralSharingGrant> findAllByReferralIdIn(Collection<UUID> referralIds);

	@Query("""
			select grant from ReferralSharingGrant grant, ReferralRequest referral
			where grant.referralId = referral.id
			  and grant.organisationId = :organisationId
			  and grant.recipientUserId = :recipientUserId
			  and grant.recipientMembershipId = :recipientMembershipId
			  and grant.patientRegistrationId = :patientRegistrationId
			  and referral.senderUserId = :ownerUserId
			  and referral.senderMembershipId = :ownerMembershipId
			  and grant.status = com.sahha.communication.entity.SharingGrantStatus.ACTIVE
			  and referral.status = com.sahha.communication.entity.ReferralStatus.ACTIVE
			  and grant.validFrom <= :now and grant.validUntil > :now
			order by grant.validUntil desc, grant.id
			""")
	List<ReferralSharingGrant> findActiveForOwner(
			@Param("organisationId") UUID organisationId,
			@Param("recipientUserId") UUID recipientUserId,
			@Param("recipientMembershipId") UUID recipientMembershipId,
			@Param("patientRegistrationId") UUID patientRegistrationId,
			@Param("ownerUserId") UUID ownerUserId,
			@Param("ownerMembershipId") UUID ownerMembershipId,
			@Param("now") Instant now,
			Pageable pageable);

	@Query("""
			select grant from ReferralSharingGrant grant,
				ReferralRequest referral, ReferralShareItem item
			where grant.referralId = referral.id
			  and item.referralId = referral.id
			  and grant.organisationId = :organisationId
			  and grant.recipientUserId = :recipientUserId
			  and grant.recipientMembershipId = :recipientMembershipId
			  and grant.patientRegistrationId = :patientRegistrationId
			  and grant.status = com.sahha.communication.entity.SharingGrantStatus.ACTIVE
			  and referral.status = com.sahha.communication.entity.ReferralStatus.ACTIVE
			  and grant.validFrom <= :now
			  and grant.validUntil > :now
			  and item.resourceType = :resourceType
			  and item.resourceId = :resourceId
			order by grant.validUntil desc, grant.id
			""")
	List<ReferralSharingGrant> findActiveForResource(
			@Param("organisationId") UUID organisationId,
			@Param("recipientUserId") UUID recipientUserId,
			@Param("recipientMembershipId") UUID recipientMembershipId,
			@Param("patientRegistrationId") UUID patientRegistrationId,
			@Param("resourceType") ShareResourceType resourceType,
			@Param("resourceId") UUID resourceId,
			@Param("now") Instant now,
			Pageable pageable);
}
