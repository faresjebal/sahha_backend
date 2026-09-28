package com.sahha.communication.service.referralservice;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.communication.client.organisation.OrganisationCollaborationClient;
import com.sahha.communication.dto.response.CareAccessDecisionResponse;
import com.sahha.communication.entity.CommunicationAuditEvent;
import com.sahha.communication.entity.ReferralRequest;
import com.sahha.communication.entity.ReferralSharingGrant;
import com.sahha.communication.exception.ConversationNotFoundException;
import com.sahha.communication.repository.CommunicationAuditEventRepository;
import com.sahha.communication.repository.ReferralCareParticipationRepository;
import com.sahha.communication.repository.ReferralRequestRepository;

/** Communication owns the consent/participation authority. Clinical and File
 * must still scope their own records to this organisation/patient and finality. */
@Service
public class ReferralCareAccessService {
    private final ReferralCareParticipationRepository participations;
    private final ReferralRequestRepository referrals;
    private final OrganisationCollaborationClient directory;
    private final CommunicationAuditEventRepository audits;
    private final Clock clock;

    public ReferralCareAccessService(ReferralCareParticipationRepository participations,
            ReferralRequestRepository referrals, OrganisationCollaborationClient directory,
            CommunicationAuditEventRepository audits, Clock clock) {
        this.participations = participations; this.referrals = referrals;
        this.directory = directory; this.audits = audits; this.clock = clock;
    }

    @Transactional
    public CareAccessDecisionResponse decide(UUID organisationId, UUID patientRegistrationId,
            UUID actorUserId, String accessToken) {
        Objects.requireNonNull(patientRegistrationId);
        ReferralSharingGrant allowed = null;
        try {
            var actor = directory.resolve(organisationId, actorUserId, accessToken);
            // Do not cache an authority: termination and expiry must apply on the next read.
            for (int page = 0; allowed == null; page++) {
                var candidates = participations.findActiveGrants(organisationId, patientRegistrationId,
                        actorUserId, actor.membershipId(), clock.instant(), PageRequest.of(page, 50));
                for (var grant : candidates) {
                    ReferralRequest referral = referrals.findById(grant.getReferralId()).orElseThrow();
                    if (membershipsMatch(referral, accessToken)) {
                        allowed = grant;
                        break;
                    }
                }
                if (candidates.size() < 50) break;
            }
        }
        catch (ConversationNotFoundException missingActor) {
            // Denial reveals neither a patient nor any former care participation.
        }
        // Membership lookups can cross the expiry boundary; check again before returning.
        if (allowed != null && !allowed.getValidUntil().isAfter(clock.instant())) allowed = null;
        audits.save(CommunicationAuditEvent.recordAccessDecision(organisationId,
                allowed == null ? UUID.randomUUID() : allowed.getId(),
                allowed == null ? "CARE_ACCESS_DECISION" : "SHARE_GRANT",
                patientRegistrationId, "PATIENT_REGISTRATION", actorUserId,
                allowed == null ? "CARE_ACCESS_DENIED" : "CARE_ACCESS_ALLOWED", clock.instant()));
        return allowed == null ? CareAccessDecisionResponse.denied()
                : new CareAccessDecisionResponse(true, organisationId, patientRegistrationId, actorUserId,
                        allowed.getId(), allowed.getReferralId(), allowed.getValidUntil());
    }

    private boolean membershipsMatch(ReferralRequest referral, String token) {
        try {
            return referral.getSenderMembershipId().equals(directory.resolve(referral.getOrganisationId(),
                    referral.getSenderUserId(), token).membershipId())
                    && referral.getRecipientMembershipId().equals(directory.resolve(referral.getOrganisationId(),
                    referral.getRecipientUserId(), token).membershipId());
        }
        catch (ConversationNotFoundException formerMember) {
            // A different, independently consented referral may still authorise this doctor.
            return false;
        }
        // Authentication/permission failures and dependency outages are never converted to grants.
    }
}
