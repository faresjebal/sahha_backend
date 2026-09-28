package com.sahha.communication.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.communication.entity.ReferralRequest;
import com.sahha.communication.entity.ReferralStatus;

public interface ReferralRequestRepository extends JpaRepository<ReferralRequest, UUID> {
	Optional<ReferralRequest> findByOrganisationIdAndSenderUserIdAndRequestId(
			UUID organisationId, UUID senderUserId, UUID requestId);

	@Query("""
			select referral from ReferralRequest referral
			where referral.id = :referralId
			  and referral.organisationId = :organisationId
			  and ((referral.senderUserId = :userId
			        and referral.senderMembershipId = :membershipId)
			    or (referral.recipientUserId = :userId
			        and referral.recipientMembershipId = :membershipId
			        and referral.sentAt is not null))
			""")
	Optional<ReferralRequest> findParticipantReferral(
			@Param("referralId") UUID referralId,
			@Param("organisationId") UUID organisationId,
			@Param("userId") UUID userId,
			@Param("membershipId") UUID membershipId);

	Page<ReferralRequest> findAllByOrganisationIdAndSenderUserIdAndSenderMembershipIdOrderByCreatedAtDescIdDesc(
			UUID organisationId, UUID senderUserId, UUID senderMembershipId, Pageable pageable);

	@Query("""
			select referral from ReferralRequest referral
			where referral.organisationId = :organisationId
			  and referral.recipientUserId = :recipientUserId
			  and referral.recipientMembershipId = :recipientMembershipId
			  and referral.sentAt is not null
			order by referral.createdAt desc, referral.id desc
			""")
	Page<ReferralRequest> findAllByOrganisationIdAndRecipientUserIdAndRecipientMembershipIdOrderByCreatedAtDescIdDesc(
			@Param("organisationId") UUID organisationId, @Param("recipientUserId") UUID recipientUserId,
			@Param("recipientMembershipId") UUID recipientMembershipId, Pageable pageable);

	@Query("""
			select referral from ReferralRequest referral
			where referral.organisationId = :organisationId
			  and ((referral.senderUserId = :userId
			        and referral.senderMembershipId = :membershipId)
			    or (referral.recipientUserId = :userId
			        and referral.recipientMembershipId = :membershipId
			        and referral.sentAt is not null))
			order by referral.createdAt desc, referral.id desc
			""")
	Page<ReferralRequest> findParticipantReferrals(
			@Param("organisationId") UUID organisationId,
			@Param("userId") UUID userId,
			@Param("membershipId") UUID membershipId,
			Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select referral from ReferralRequest referral
			where referral.status in :statuses
			  and referral.accessExpiresAt <= :now
			order by referral.accessExpiresAt, referral.id
			""")
	List<ReferralRequest> findExpiryCandidates(
			@Param("statuses") Collection<ReferralStatus> statuses,
			@Param("now") Instant now, Pageable pageable);
}
