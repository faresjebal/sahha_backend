package com.sahha.organisation.service.staffinvitationservice;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.client.auth.AuthAccountResource;
import com.sahha.organisation.config.StaffInvitationProperties;
import com.sahha.organisation.dto.request.CreateStaffInvitationRequest;
import com.sahha.organisation.dto.response.StaffInvitationPageResponse;
import com.sahha.organisation.dto.response.StaffInvitationResponse;
import com.sahha.organisation.entity.Organisation;
import com.sahha.organisation.entity.OrganisationAuditEvent;
import com.sahha.organisation.entity.OrganisationAuditEventType;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipRole;
import com.sahha.organisation.entity.OrganisationOutboxEvent;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.StaffInvitation;
import com.sahha.organisation.entity.StaffInvitationStatus;
import com.sahha.organisation.event.OrganisationEventMapper;
import com.sahha.organisation.exception.ConcurrentStaffInvitationModificationException;
import com.sahha.organisation.exception.OrganisationContextNotFoundException;
import com.sahha.organisation.exception.StaffInvitationConflictException;
import com.sahha.organisation.exception.StaffInvitationNotFoundException;
import com.sahha.organisation.mapper.StaffInvitationMapper;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.repository.OrganisationRepository;
import com.sahha.organisation.repository.StaffInvitationRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationContextService;

@Service
public class StaffInvitationService {

	private final StaffInvitationRepository invitationRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final OrganisationMembershipRoleRepository roleRepository;
	private final OrganisationRepository organisationRepository;
	private final OrganisationAuditEventRepository auditRepository;
	private final OrganisationOutboxEventRepository outboxRepository;
	private final OrganisationContextService contextService;
	private final StaffInvitationMapper invitationMapper;
	private final OrganisationEventMapper eventMapper;
	private final StaffInvitationProperties properties;
	private final Clock clock;

	public StaffInvitationService(
			StaffInvitationRepository invitationRepository,
			OrganisationMembershipRepository membershipRepository,
			OrganisationMembershipRoleRepository roleRepository,
			OrganisationRepository organisationRepository,
			OrganisationAuditEventRepository auditRepository,
			OrganisationOutboxEventRepository outboxRepository,
			OrganisationContextService contextService,
			StaffInvitationMapper invitationMapper,
			OrganisationEventMapper eventMapper,
			StaffInvitationProperties properties,
			Clock clock) {
		this.invitationRepository = invitationRepository;
		this.membershipRepository = membershipRepository;
		this.roleRepository = roleRepository;
		this.organisationRepository = organisationRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.contextService = contextService;
		this.invitationMapper = invitationMapper;
		this.eventMapper = eventMapper;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public StaffInvitationResponse create(
			UUID organisationId,
			CreateStaffInvitationRequest request,
			UUID actorUserId,
			String requestId) {
		requireAdministrator(organisationId, actorUserId);
		Instant now = clock.instant();
		StaffInvitation invitation;
		try {
			invitation = invitationRepository.saveAndFlush(
					StaffInvitation.create(
							organisationId,
							request.email(),
							request.role(),
							actorUserId,
							now,
							properties.validity()));
		}
		catch (DataIntegrityViolationException conflict) {
			throw new StaffInvitationConflictException();
		}
		recordChange(
				invitation,
				OrganisationAuditEventType.STAFF_INVITATION_CREATED,
				actorUserId,
				null,
				requestId,
				now);
		return toResponse(invitation, now);
	}

	@Transactional(readOnly = true)
	public StaffInvitationPageResponse listForAdministrator(
			UUID organisationId,
			UUID actorUserId,
			int page,
			int size) {
		requireAdministrator(organisationId, actorUserId);
		return response(invitationRepository.findAllByOrganisationId(
				organisationId,
				pageRequest(page, size)), clock.instant());
	}

	@Transactional(readOnly = true)
	public StaffInvitationResponse findForAdministrator(
			UUID organisationId,
			UUID invitationId,
			UUID actorUserId) {
		requireAdministrator(organisationId, actorUserId);
		Instant now = clock.instant();
		return toResponse(findScoped(organisationId, invitationId), now);
	}

	@Transactional
	public StaffInvitationResponse renew(
			UUID organisationId,
			UUID invitationId,
			long version,
			UUID actorUserId,
			String requestId) {
		requireAdministrator(organisationId, actorUserId);
		StaffInvitation invitation = findScoped(organisationId, invitationId);
		requireVersion(invitation, version);
		Instant now = clock.instant();
		try {
			invitation.renew(now, properties.validity());
			flush(invitation);
		}
		catch (IllegalStateException invalidState) {
			throw new StaffInvitationConflictException();
		}
		recordChange(
				invitation,
				OrganisationAuditEventType.STAFF_INVITATION_RENEWED,
				actorUserId,
				null,
				requestId,
				now);
		return toResponse(invitation, now);
	}

	@Transactional
	public StaffInvitationResponse revoke(
			UUID organisationId,
			UUID invitationId,
			long version,
			UUID actorUserId,
			String requestId) {
		requireAdministrator(organisationId, actorUserId);
		StaffInvitation invitation = findScoped(organisationId, invitationId);
		requireVersion(invitation, version);
		Instant now = clock.instant();
		try {
			invitation.revoke(actorUserId, now);
			flush(invitation);
		}
		catch (IllegalStateException invalidState) {
			throw new StaffInvitationConflictException();
		}
		recordChange(
				invitation,
				OrganisationAuditEventType.STAFF_INVITATION_REVOKED,
				actorUserId,
				null,
				requestId,
				now);
		return toResponse(invitation, now);
	}

	@Transactional(readOnly = true)
	public StaffInvitationPageResponse listMine(
			AuthAccountResource account,
			int page,
			int size) {
		return response(invitationRepository.findAllByNormalizedEmail(
				StaffInvitation.normalizeEmail(account.email()),
				pageRequest(page, size)), clock.instant());
	}

	@Transactional
	public StaffInvitationResponse accept(
			UUID invitationId,
			long version,
			AuthAccountResource account,
			String requestId) {
		UUID actorUserId = account.id();
		StaffInvitation invitation = findMineForUpdate(invitationId, account);
		requireVersion(invitation, version);
		Instant now = clock.instant();
		if (invitation.statusAt(now) != StaffInvitationStatus.PENDING
				|| membershipRepository.existsByOrganisationIdAndUserId(
						invitation.getOrganisationId(),
						actorUserId)) {
			throw new StaffInvitationConflictException();
		}

		OrganisationMembership membership = OrganisationMembership.activate(
				invitation.getOrganisationId(),
				actorUserId,
				account.email(),
				account.firstName(),
				account.lastName(),
				invitation.getCreatedBy(),
				now);
		try {
			membershipRepository.saveAndFlush(membership);
			roleRepository.saveAndFlush(OrganisationMembershipRole.assign(
					membership,
					invitation.getRole(),
					invitation.getCreatedBy(),
					now));
			invitation.accept(actorUserId, membership.getId(), now);
			invitationRepository.saveAndFlush(invitation);
		}
		catch (DataIntegrityViolationException conflict) {
			throw new StaffInvitationConflictException();
		}
		recordChange(
				invitation,
				OrganisationAuditEventType.STAFF_INVITATION_ACCEPTED,
				actorUserId,
				actorUserId,
				requestId,
				now);
		return toResponse(invitation, now);
	}

	@Transactional
	public StaffInvitationResponse reject(
			UUID invitationId,
			long version,
			AuthAccountResource account,
			String requestId) {
		UUID actorUserId = account.id();
		StaffInvitation invitation = findMineForUpdate(invitationId, account);
		requireVersion(invitation, version);
		Instant now = clock.instant();
		try {
			invitation.reject(actorUserId, now);
			invitationRepository.saveAndFlush(invitation);
		}
		catch (IllegalStateException invalidState) {
			throw new StaffInvitationConflictException();
		}
		recordChange(
				invitation,
				OrganisationAuditEventType.STAFF_INVITATION_REJECTED,
				actorUserId,
				actorUserId,
				requestId,
				now);
		return toResponse(invitation, now);
	}

	private StaffInvitation findMineForUpdate(
			UUID invitationId,
			AuthAccountResource account) {
		StaffInvitation invitation = invitationRepository
				.findByIdForUpdate(invitationId)
				.orElseThrow(StaffInvitationNotFoundException::new);
		if (!invitation.targets(account.email())) {
			throw new StaffInvitationNotFoundException();
		}
		return invitation;
	}

	private void requireAdministrator(UUID organisationId, UUID actorUserId) {
		contextService.requireActiveRole(
				organisationId,
				actorUserId,
				OrganisationRole.ORGANIZATION_ADMIN);
	}

	private StaffInvitation findScoped(
			UUID organisationId,
			UUID invitationId) {
		return invitationRepository
				.findByIdAndOrganisationId(invitationId, organisationId)
				.orElseThrow(StaffInvitationNotFoundException::new);
	}

	private void flush(StaffInvitation invitation) {
		try {
			invitationRepository.saveAndFlush(invitation);
		}
		catch (ObjectOptimisticLockingFailureException concurrentChange) {
			throw new ConcurrentStaffInvitationModificationException();
		}
		catch (DataIntegrityViolationException conflict) {
			throw new StaffInvitationConflictException();
		}
	}

	private void recordChange(
			StaffInvitation invitation,
			OrganisationAuditEventType eventType,
			UUID actorUserId,
			UUID targetUserId,
			String requestId,
			Instant occurredAt) {
		OrganisationAuditEvent audit = auditRepository.save(
				OrganisationAuditEvent.staffInvitationChanged(
						invitation,
						eventType,
						actorUserId,
						targetUserId,
						requestId,
						occurredAt));
		outboxRepository.save(OrganisationOutboxEvent.pending(
				audit,
				eventMapper.toPayload(invitation, audit)));
	}

	private StaffInvitationPageResponse response(
			Page<StaffInvitation> invitations,
			Instant observedAt) {
		Map<UUID, Organisation> organisations = organisationRepository
				.findAllById(invitations.getContent().stream()
						.map(StaffInvitation::getOrganisationId)
						.distinct()
						.toList())
				.stream()
				.collect(Collectors.toUnmodifiableMap(
						Organisation::getId,
						Function.identity()));
		return new StaffInvitationPageResponse(
				invitations.getContent().stream()
						.map(invitation -> invitationMapper.toResponse(
								invitation,
								requireOrganisation(
										organisations,
										invitation.getOrganisationId())
										.getName(),
								observedAt))
						.toList(),
				invitations.getNumber(),
				invitations.getSize(),
				invitations.getTotalElements(),
				invitations.getTotalPages());
	}

	private StaffInvitationResponse toResponse(
			StaffInvitation invitation,
			Instant observedAt) {
		Organisation organisation = organisationRepository
				.findById(invitation.getOrganisationId())
				.orElseThrow(OrganisationContextNotFoundException::new);
		return invitationMapper.toResponse(
				invitation,
				organisation.getName(),
				observedAt);
	}

	private static Organisation requireOrganisation(
			Map<UUID, Organisation> organisations,
			UUID organisationId) {
		Organisation organisation = organisations.get(organisationId);
		if (organisation == null) {
			throw new OrganisationContextNotFoundException();
		}
		return organisation;
	}

	private static PageRequest pageRequest(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException(
					"page must be non-negative and size must be between 1 and 100");
		}
		return PageRequest.of(
				page,
				size,
				Sort.by(
						Sort.Order.desc("createdAt"),
						Sort.Order.asc("id")));
	}

	private static void requireVersion(
			StaffInvitation invitation,
			long version) {
		if (invitation.getVersion() != version) {
			throw new ConcurrentStaffInvitationModificationException();
		}
	}
}
