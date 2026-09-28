package com.sahha.communication.service.referralservice;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.communication.client.organisation.CollaborationDoctorResource;
import com.sahha.communication.client.organisation.OrganisationCollaborationClient;
import com.sahha.communication.service.conversationservice.CollaborationPatientContextService;
import com.sahha.communication.config.CommunicationOutboxProperties;
import com.sahha.communication.dto.request.CreateReferralRequest;
import com.sahha.communication.dto.request.ReferralReasonedCommandRequest;
import com.sahha.communication.dto.request.ReferralShareItemRequest;
import com.sahha.communication.dto.request.ReferralVersionRequest;
import com.sahha.communication.dto.request.ShareAccessDecisionRequest;
import com.sahha.communication.dto.response.ReferralPageResponse;
import com.sahha.communication.dto.response.ReferralResponse;
import com.sahha.communication.dto.response.ReferralShareItemResponse;
import com.sahha.communication.dto.response.ShareAccessDecisionResponse;
import com.sahha.communication.entity.CommunicationAuditEvent;
import com.sahha.communication.entity.CommunicationOutboxEvent;
import com.sahha.communication.entity.ReferralRequest;
import com.sahha.communication.entity.ReferralCareParticipation;
import com.sahha.communication.entity.ReferralType;
import com.sahha.communication.entity.ReferralShareItem;
import com.sahha.communication.entity.ReferralSharingGrant;
import com.sahha.communication.entity.ReferralStatus;
import com.sahha.communication.event.CommunicationEventMapper;
import com.sahha.communication.exception.ConversationNotFoundException;
import com.sahha.communication.exception.ReferralNotFoundException;
import com.sahha.communication.exception.ReferralStateConflictException;
import com.sahha.communication.repository.CommunicationAuditEventRepository;
import com.sahha.communication.repository.CommunicationOutboxEventRepository;
import com.sahha.communication.repository.ReferralRequestRepository;
import com.sahha.communication.repository.ReferralCareParticipationRepository;
import com.sahha.communication.repository.ReferralShareItemRepository;
import com.sahha.communication.repository.ReferralSharingGrantRepository;

@Service
public class ReferralService {
	private static final UUID SYSTEM_ACTOR_ID = new UUID(0, 0);
	private static final int MAXIMUM_PAGE_SIZE = 100;

	private final ReferralRequestRepository referralRepository;
	private final ReferralShareItemRepository itemRepository;
	private final ReferralSharingGrantRepository grantRepository;
	private final ReferralCareParticipationRepository careRepository;
	private final CommunicationAuditEventRepository auditRepository;
	private final CommunicationOutboxEventRepository outboxRepository;
	private final OrganisationCollaborationClient organisationClient;
	private final CollaborationPatientContextService patientContextClient;
	private final CommunicationEventMapper eventMapper;
	private final CommunicationOutboxProperties outboxProperties;
	private final Clock clock;
	private final ShareSelectionDecisionCache selectionCache;

	public ReferralService(ReferralRequestRepository referralRepository,
			ReferralShareItemRepository itemRepository,
			ReferralSharingGrantRepository grantRepository,
			ReferralCareParticipationRepository careRepository,
			CommunicationAuditEventRepository auditRepository,
			CommunicationOutboxEventRepository outboxRepository,
			OrganisationCollaborationClient organisationClient,
			CollaborationPatientContextService patientContextClient,
			CommunicationEventMapper eventMapper,
			CommunicationOutboxProperties outboxProperties,
			Clock clock, ShareSelectionDecisionCache selectionCache) {
		this.referralRepository = referralRepository;
		this.itemRepository = itemRepository;
		this.grantRepository = grantRepository;
		this.careRepository = careRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.organisationClient = organisationClient;
		this.patientContextClient = patientContextClient;
		this.eventMapper = eventMapper;
		this.outboxProperties = outboxProperties;
		this.clock = clock;
		this.selectionCache = selectionCache;
	}

	@Transactional
	public ReferralResponse create(UUID organisationId, UUID actorUserId,
			String accessToken, CreateReferralRequest request) {
		if (actorUserId.equals(request.recipientUserId())) {
			throw new IllegalArgumentException("recipient must be another doctor");
		}
		Set<ShareKey> requestedItems = requestedItems(request.selectedItems(), request.referralType());
		CollaborationDoctorResource actor = resolveReferralDoctor(
				organisationId, actorUserId, accessToken);
		CollaborationDoctorResource recipient = resolveReferralDoctor(
				organisationId, request.recipientUserId(), accessToken);
		requirePatientAccess(organisationId, request.patientRegistrationId(),
				actorUserId, accessToken, request.sourceConsultationId());

		ReferralRequest existing = referralRepository
				.findByOrganisationIdAndSenderUserIdAndRequestId(
						organisationId, actorUserId, request.referralRequestId())
				.orElse(null);
		if (existing != null) {
			List<ReferralShareItem> items = items(existing.getId());
			if (!sameRequest(existing, items, request, requestedItems)) {
				throw new ReferralStateConflictException();
			}
			return response(existing, items, grantRepository.findByReferralId(existing.getId()).orElse(null));
		}

		Instant now = clock.instant();
		ReferralRequest referral = referralRepository.saveAndFlush(ReferralRequest.createWithSource(
				organisationId, request.referralRequestId(), request.patientRegistrationId(),
				actorUserId, actor.membershipId(), actor.displayName(), recipient.userId(),
				recipient.membershipId(), recipient.displayName(), request.reason(),
				request.priority(), request.clinicalSummary(), request.purpose(),
				request.consentType(), request.consentEvidenceReference(),
				request.consentRecordedAt(), request.accessExpiresAt(),
				request.sendImmediately(), now, request.sourceConsultationId(), request.referralType()));
		List<ReferralShareItem> items = request.selectedItems().stream()
				.map(item -> ReferralShareItem.select(referral.getId(), organisationId,
						item.resourceType(), item.resourceId(), now)).toList();
		itemRepository.saveAll(items);
		String auditType = request.sendImmediately() ? "REFERRAL_SENT" : "REFERRAL_DRAFTED";
		CommunicationAuditEvent audit = auditRepository.save(
				CommunicationAuditEvent.recordReferral(organisationId, referral.getId(),
						actorUserId, auditType, referral.getVersion(), now));
		if (request.sendImmediately()) {
			publish(audit, referral, "referral.sent.v1", List.of(recipient.userId()));
		}
		return response(referral, items, null);
	}

	@Transactional(readOnly = true)
	public ReferralPageResponse list(UUID organisationId, UUID actorUserId,
			String accessToken, ReferralDirection direction, int page, int size) {
		validatePage(page, size);
		CollaborationDoctorResource actor = resolveReferralDoctor(
				organisationId, actorUserId, accessToken);
		PageRequest pageable = PageRequest.of(page, size);
		Page<ReferralRequest> result = switch (Objects.requireNonNull(direction)) {
			case SENT -> referralRepository
					.findAllByOrganisationIdAndSenderUserIdAndSenderMembershipIdOrderByCreatedAtDescIdDesc(
							organisationId, actorUserId, actor.membershipId(), pageable);
			case RECEIVED -> referralRepository
					.findAllByOrganisationIdAndRecipientUserIdAndRecipientMembershipIdOrderByCreatedAtDescIdDesc(
							organisationId, actorUserId, actor.membershipId(), pageable);
			case ALL -> referralRepository.findParticipantReferrals(
					organisationId, actorUserId, actor.membershipId(), pageable);
		};
		List<ReferralResponse> responses = responses(result.getContent());
		return new ReferralPageResponse(responses, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ReferralResponse find(UUID referralId, UUID organisationId,
			UUID actorUserId, String accessToken) {
		CollaborationDoctorResource actor = resolveReferralDoctor(
				organisationId, actorUserId, accessToken);
		ReferralRequest referral = participantReferral(referralId, organisationId,
				actorUserId, actor.membershipId());
		return response(referral, items(referralId),
				grantRepository.findByReferralId(referralId).orElse(null));
	}

	@Transactional
	public ReferralResponse send(UUID referralId, UUID organisationId, UUID actorUserId,
			String accessToken, ReferralVersionRequest request) {
		CollaborationDoctorResource actor = resolveReferralDoctor(
				organisationId, actorUserId, accessToken);
		ReferralRequest referral = participantReferral(referralId, organisationId,
				actorUserId, actor.membershipId());
		requireSender(referral, actor);
		CollaborationDoctorResource recipient = resolveReferralDoctor(
				organisationId, referral.getRecipientUserId(), accessToken);
		requireMembership(recipient, referral.getRecipientMembershipId());
		requirePatientAccess(organisationId, referral.getPatientRegistrationId(),
				actorUserId, accessToken, referral.getSourceConsultationId());
		Instant now = clock.instant();
		referral.send(actorUserId, request.expectedVersion(), now);
		referralRepository.saveAndFlush(referral);
		CommunicationAuditEvent audit = referralAudit(referral, actorUserId,
				"REFERRAL_SENT", now);
		publish(audit, referral, "referral.sent.v1", List.of(recipient.userId()));
		return response(referral, items(referralId), null);
	}

	@Transactional
	public ReferralResponse accept(UUID referralId, UUID organisationId, UUID actorUserId,
			String accessToken, ReferralVersionRequest request) {
		CollaborationDoctorResource actor = resolveReferralDoctor(
				organisationId, actorUserId, accessToken);
		ReferralRequest referral = participantReferral(referralId, organisationId,
				actorUserId, actor.membershipId());
		requireRecipient(referral, actor);
		CollaborationDoctorResource sender = resolveReferralDoctor(
				organisationId, referral.getSenderUserId(), accessToken);
		requireMembership(sender, referral.getSenderMembershipId());
		Instant now = clock.instant();
		referral.accept(actorUserId, request.expectedVersion(), now);
		referralRepository.saveAndFlush(referral);
		ReferralSharingGrant grant = grantRepository.saveAndFlush(
				ReferralSharingGrant.activate(referral, now));
		if (referral.getReferralType() == ReferralType.SHARED_TREATMENT) {
			careRepository.saveAllAndFlush(ReferralCareParticipation.begin(referral, grant))
					.forEach(care -> auditRepository.save(CommunicationAuditEvent.recordResourceChange(
							organisationId, "CARE_PARTICIPATION", care.getId(), "SHARE_GRANT",
							grant.getId(), actorUserId, "SHARED_CARE_PARTICIPATION_STARTED", 0, now)));
		}
		grantAudit(grant, actorUserId, "SHARING_GRANT_ACTIVATED", now);
		CommunicationAuditEvent audit = referralAudit(referral, actorUserId,
				"REFERRAL_ACCEPTED", now);
		publish(audit, referral, "referral.accepted.v1", List.of(sender.userId()));
		return response(referral, items(referralId), grant);
	}

	@Transactional
	public ReferralResponse reject(UUID referralId, UUID organisationId, UUID actorUserId,
			String accessToken, ReferralReasonedCommandRequest request) {
		CollaborationDoctorResource actor = resolveReferralDoctor(
				organisationId, actorUserId, accessToken);
		ReferralRequest referral = participantReferral(referralId, organisationId,
				actorUserId, actor.membershipId());
		requireRecipient(referral, actor);
		Instant now = clock.instant();
		referral.reject(actorUserId, request.expectedVersion(), request.reason(), now);
		referralRepository.saveAndFlush(referral);
		CommunicationAuditEvent audit = referralAudit(referral, actorUserId,
				"REFERRAL_REJECTED", now);
		publish(audit, referral, "referral.rejected.v1", List.of(referral.getSenderUserId()));
		return response(referral, items(referralId), null);
	}

	@Transactional
	public ReferralResponse revoke(UUID referralId, UUID organisationId, UUID actorUserId,
			String accessToken, ReferralReasonedCommandRequest request) {
		CollaborationDoctorResource actor = resolveReferralDoctor(
				organisationId, actorUserId, accessToken);
		ReferralRequest referral = participantReferral(referralId, organisationId,
				actorUserId, actor.membershipId());
		requireSender(referral, actor);
		Instant now = clock.instant();
		referral.revoke(actorUserId, request.expectedVersion(), request.reason(), now);
		referralRepository.saveAndFlush(referral);
		ReferralSharingGrant grant = grantRepository.findByReferralId(referralId).orElse(null);
		if (grant != null) {
			grant.revoke(now, request.reason().strip());
			grantRepository.saveAndFlush(grant);
			grantAudit(grant, actorUserId, "SHARING_GRANT_REVOKED", now);
		}
		CommunicationAuditEvent audit = referralAudit(referral, actorUserId,
				"REFERRAL_REVOKED", now);
		if (referral.getSentAt() != null) {
			publish(audit, referral, "referral.revoked.v1", List.of(referral.getRecipientUserId()));
		}
		return response(referral, items(referralId), grant);
	}

	@Transactional
	public ReferralResponse complete(UUID referralId, UUID organisationId, UUID actorUserId,
			String accessToken, ReferralVersionRequest request) {
		CollaborationDoctorResource actor = resolveReferralDoctor(
				organisationId, actorUserId, accessToken);
		ReferralRequest referral = participantReferral(referralId, organisationId,
				actorUserId, actor.membershipId());
		Instant now = clock.instant();
		referral.complete(actorUserId, request.expectedVersion(), now);
		referralRepository.saveAndFlush(referral);
		ReferralSharingGrant grant = grantRepository.findByReferralId(referralId).orElse(null);
		if (grant != null) {
			grant.revoke(now, "REFERRAL_COMPLETED");
			grantRepository.saveAndFlush(grant);
			grantAudit(grant, actorUserId, "SHARING_GRANT_DEACTIVATED", now);
		}
		UUID otherParticipant = actorUserId.equals(referral.getSenderUserId())
				? referral.getRecipientUserId() : referral.getSenderUserId();
		CommunicationAuditEvent audit = referralAudit(referral, actorUserId,
				"REFERRAL_COMPLETED", now);
		publish(audit, referral, "referral.completed.v1", List.of(otherParticipant));
		return response(referral, items(referralId), grant);
	}

	@Transactional
	public ShareAccessDecisionResponse decide(UUID organisationId, UUID actorUserId,
			String accessToken, ShareAccessDecisionRequest request) {
		CollaborationDoctorResource actor = resolveReferralDoctor(
				organisationId, actorUserId, accessToken);
		Instant now = clock.instant();
		CollaborationDoctorResource owner = resolveReferralDoctor(
				organisationId, request.resourceOwnerUserId(), accessToken);
		ReferralSharingGrant grant = null;
		if (request.resourceOwnerMembershipId() == null
				|| request.resourceOwnerMembershipId().equals(owner.membershipId())) {
			// Always consult PostgreSQL before Redis: no stale cache entry can extend a grant.
			for (int page = 0; grant == null; page++) {
				List<ReferralSharingGrant> candidates = grantRepository.findActiveForOwner(
						organisationId, actorUserId, actor.membershipId(),
						request.patientRegistrationId(), owner.userId(), owner.membershipId(),
						now, PageRequest.of(page, 50));
				for (ReferralSharingGrant candidate : candidates) {
					if (selectionCache.selected(candidate, request, () ->
							itemRepository.existsByReferralIdAndResourceTypeAndResourceId(
									candidate.getReferralId(), request.resourceType(), request.resourceId()))) {
						grant = candidate;
						break;
					}
				}
				if (candidates.size() < 50) break;
			}
		}
		boolean allowed = grant != null;
		auditRepository.save(CommunicationAuditEvent.recordAccessDecision(
				organisationId, allowed ? grant.getId() : UUID.randomUUID(),
				allowed ? "SHARE_GRANT" : "SHARE_ACCESS_DECISION",
				request.resourceId(), request.resourceType().name(), actorUserId,
				allowed ? "SHARE_ACCESS_ALLOWED" : "SHARE_ACCESS_DENIED", now));
		return allowed
				? new ShareAccessDecisionResponse(true, grant.getId(),
						grant.getReferralId(), grant.getValidUntil())
				: ShareAccessDecisionResponse.denied();
	}

	@Transactional
	public int expireDue() {
		Instant now = clock.instant();
		List<ReferralRequest> referrals = referralRepository.findExpiryCandidates(
				List.of(ReferralStatus.SENT, ReferralStatus.ACTIVE), now,
				PageRequest.of(0, 50));
		int expired = 0;
		for (ReferralRequest referral : referrals) {
			if (!referral.expire(SYSTEM_ACTOR_ID, now)) continue;
			referralRepository.saveAndFlush(referral);
			ReferralSharingGrant grant = grantRepository.findByReferralId(referral.getId()).orElse(null);
			if (grant != null) {
				grant.expire(now);
				grantRepository.saveAndFlush(grant);
				grantAudit(grant, SYSTEM_ACTOR_ID, "SHARING_GRANT_EXPIRED", now);
			}
			CommunicationAuditEvent audit = referralAudit(referral, SYSTEM_ACTOR_ID,
					"REFERRAL_EXPIRED", now);
			publish(audit, referral, "referral.expired.v1",
					List.of(referral.getSenderUserId(), referral.getRecipientUserId()));
			expired++;
		}
		return expired;
	}

	private CommunicationAuditEvent referralAudit(ReferralRequest referral,
			UUID actorUserId, String type, Instant now) {
		return auditRepository.save(CommunicationAuditEvent.recordReferral(
				referral.getOrganisationId(), referral.getId(), actorUserId,
				type, referral.getVersion(), now));
	}

	private void grantAudit(ReferralSharingGrant grant, UUID actorUserId,
			String type, Instant now) {
		selectionCache.invalidateAfterCommit(grant);
		auditRepository.save(CommunicationAuditEvent.recordResourceChange(
				grant.getOrganisationId(), "SHARE_GRANT", grant.getId(),
				"REFERRAL", grant.getReferralId(), actorUserId, type,
				grant.getVersion(), now));
	}

	private void publish(CommunicationAuditEvent audit, ReferralRequest referral,
			String type, List<UUID> recipients) {
		outboxRepository.save(CommunicationOutboxEvent.pending(audit,
				outboxProperties.referralsTopic(), type,
				eventMapper.referralChanged(audit, referral, type, recipients)));
	}

	private CollaborationDoctorResource resolveReferralDoctor(UUID organisationId,
			UUID userId, String accessToken) {
		try { return organisationClient.resolve(organisationId, userId, accessToken); }
		catch (ConversationNotFoundException missing) { throw new ReferralNotFoundException(); }
	}

	private void requirePatientAccess(UUID organisationId, UUID patientRegistrationId,
			UUID actorUserId, String accessToken, UUID sourceConsultationId) {
		try {
			patientContextClient.requireMentionable(organisationId, patientRegistrationId,
					actorUserId, accessToken, sourceConsultationId);
		}
		catch (ConversationNotFoundException missing) { throw new ReferralNotFoundException(); }
	}

	private ReferralRequest participantReferral(UUID referralId, UUID organisationId,
			UUID userId, UUID membershipId) {
		return referralRepository.findParticipantReferral(referralId, organisationId,
				userId, membershipId).orElseThrow(ReferralNotFoundException::new);
	}

	private static void requireSender(ReferralRequest referral,
			CollaborationDoctorResource actor) {
		if (!referral.getSenderUserId().equals(actor.userId())
				|| !referral.getSenderMembershipId().equals(actor.membershipId())) {
			throw new ReferralNotFoundException();
		}
	}

	private static void requireRecipient(ReferralRequest referral,
			CollaborationDoctorResource actor) {
		if (!referral.getRecipientUserId().equals(actor.userId())
				|| !referral.getRecipientMembershipId().equals(actor.membershipId())) {
			throw new ReferralNotFoundException();
		}
	}

	private static void requireMembership(CollaborationDoctorResource doctor,
			UUID expectedMembershipId) {
		if (!expectedMembershipId.equals(doctor.membershipId())) {
			throw new ReferralNotFoundException();
		}
	}

	private List<ReferralResponse> responses(List<ReferralRequest> referrals) {
		if (referrals.isEmpty()) return List.of();
		List<UUID> ids = referrals.stream().map(ReferralRequest::getId).toList();
		Map<UUID,List<ReferralShareItem>> itemsByReferral = new HashMap<>();
		itemRepository.findAllByReferralIdInOrderByReferralIdAscCreatedAtAscIdAsc(ids)
				.forEach(item -> itemsByReferral.computeIfAbsent(item.getReferralId(),
						ignored -> new ArrayList<>()).add(item));
		Map<UUID,ReferralSharingGrant> grantsByReferral = new HashMap<>();
		grantRepository.findAllByReferralIdIn(ids)
				.forEach(grant -> grantsByReferral.put(grant.getReferralId(), grant));
		return referrals.stream().map(referral -> response(referral,
				itemsByReferral.getOrDefault(referral.getId(), List.of()),
				grantsByReferral.get(referral.getId()))).toList();
	}

	private List<ReferralShareItem> items(UUID referralId) {
		return itemRepository.findAllByReferralIdOrderByCreatedAtAscIdAsc(referralId);
	}

	private static ReferralResponse response(ReferralRequest referral,
			List<ReferralShareItem> items, ReferralSharingGrant grant) {
		return new ReferralResponse(referral.getId(), referral.getOrganisationId(),
				referral.getPatientRegistrationId(), referral.getSenderUserId(),
				referral.getSenderDisplayNameSnapshot(), referral.getRecipientUserId(),
				referral.getRecipientDisplayNameSnapshot(), referral.getReason(),
				referral.getPriority(), referral.getClinicalSummary(), referral.getPurpose(),
				referral.getConsentType(), referral.getConsentEvidenceReference(),
				referral.getConsentRecordedAt(), referral.getAccessExpiresAt(),
				referral.getStatus(), grant == null ? null : grant.getId(),
				referral.getCreatedAt(), referral.getSentAt(), referral.getAcceptedAt(),
				referral.getActiveAt(), referral.getRejectedAt(), referral.getCompletedAt(),
				referral.getRevokedAt(), referral.getExpiredAt(), referral.getDecisionReason(),
				referral.getVersion(), items.stream().map(item -> new ReferralShareItemResponse(
						item.getId(), item.getResourceType(), item.getResourceId())).toList(),
				referral.getReferralType());
	}

	private static Set<ShareKey> requestedItems(List<ReferralShareItemRequest> items, ReferralType type) {
		if (items == null || items.size() > 50
				|| (type == ReferralType.SECOND_OPINION && items.isEmpty())) {
			throw new IllegalArgumentException("selected items are invalid");
		}
		Set<ShareKey> keys = new HashSet<>();
		for (ReferralShareItemRequest item : items) {
			if (item == null || item.resourceType() == null || item.resourceId() == null
					|| !keys.add(new ShareKey(item.resourceType().name(), item.resourceId()))) {
				throw new IllegalArgumentException("selected items must be unique");
			}
		}
		return Set.copyOf(keys);
	}

	private static boolean sameRequest(ReferralRequest existing,
			List<ReferralShareItem> existingItems, CreateReferralRequest request,
			Set<ShareKey> requestedItems) {
		Set<ShareKey> storedItems = existingItems.stream()
				.map(item -> new ShareKey(item.getResourceType().name(), item.getResourceId()))
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		return existing.getRecipientUserId().equals(request.recipientUserId())
				&& existing.getReferralType() == request.referralType()
				&& existing.getPatientRegistrationId().equals(request.patientRegistrationId())
				&& Objects.equals(existing.getSourceConsultationId(), request.sourceConsultationId())
				&& existing.getReason().equals(normalized(request.reason()))
				&& existing.getPriority() == request.priority()
				&& Objects.equals(existing.getClinicalSummary(), optional(request.clinicalSummary()))
				&& existing.getPurpose().equals(normalized(request.purpose()))
				&& existing.getConsentType() == request.consentType()
				&& existing.getConsentEvidenceReference().equals(
						normalized(request.consentEvidenceReference()))
				&& existing.getConsentRecordedAt().equals(request.consentRecordedAt())
				&& existing.getAccessExpiresAt().equals(request.accessExpiresAt())
				&& existing.isSubmittedImmediately() == request.sendImmediately()
				&& storedItems.equals(requestedItems);
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > MAXIMUM_PAGE_SIZE) {
			throw new IllegalArgumentException("invalid page");
		}
	}
	private static String normalized(String value) {
		return value == null ? null : value.strip().replaceAll("\\s+", " ");
	}
	private static String optional(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
	private record ShareKey(String type, UUID id) { }
}
