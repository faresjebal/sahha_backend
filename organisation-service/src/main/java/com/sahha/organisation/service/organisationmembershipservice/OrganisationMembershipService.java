package com.sahha.organisation.service.organisationmembershipservice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.client.auth.AuthAccountResource;
import com.sahha.organisation.dto.response.OrganisationMembershipPageResponse;
import com.sahha.organisation.dto.response.OrganisationMembershipResponse;
import com.sahha.organisation.entity.OrganisationAuditEvent;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipRole;
import com.sahha.organisation.entity.OrganisationOutboxEvent;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.event.OrganisationEventMapper;
import com.sahha.organisation.exception.OrganisationMembershipConflictException;
import com.sahha.organisation.exception.OrganisationMembershipNotFoundException;
import com.sahha.organisation.exception.OrganisationNotFoundException;
import com.sahha.organisation.mapper.OrganisationMembershipMapper;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.repository.OrganisationRepository;

@Service
public class OrganisationMembershipService {

	private final OrganisationRepository organisationRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final OrganisationMembershipRoleRepository roleRepository;
	private final OrganisationAuditEventRepository auditRepository;
	private final OrganisationOutboxEventRepository outboxRepository;
	private final OrganisationMembershipMapper membershipMapper;
	private final OrganisationEventMapper eventMapper;
	private final Clock clock;

	public OrganisationMembershipService(
			OrganisationRepository organisationRepository,
			OrganisationMembershipRepository membershipRepository,
			OrganisationMembershipRoleRepository roleRepository,
			OrganisationAuditEventRepository auditRepository,
			OrganisationOutboxEventRepository outboxRepository,
			OrganisationMembershipMapper membershipMapper,
			OrganisationEventMapper eventMapper,
			Clock clock) {
		this.organisationRepository = organisationRepository;
		this.membershipRepository = membershipRepository;
		this.roleRepository = roleRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.membershipMapper = membershipMapper;
		this.eventMapper = eventMapper;
		this.clock = clock;
	}

	@Transactional
	public OrganisationMembershipResponse assignAdministrator(
			UUID organisationId,
			AuthAccountResource account,
			UUID actorUserId,
			String requestId) {
		if (!organisationRepository.existsById(organisationId)) {
			throw new OrganisationNotFoundException();
		}
		if (membershipRepository.existsByOrganisationIdAndUserId(
				organisationId,
				account.id())) {
			throw new OrganisationMembershipConflictException();
		}
		Instant occurredAt = clock.instant();
		OrganisationMembership membership = membershipRepository.saveAndFlush(
				OrganisationMembership.activate(
						organisationId,
						account.id(),
						account.email(),
						account.firstName(),
						account.lastName(),
						actorUserId,
						occurredAt));
		OrganisationMembershipRole role = roleRepository.save(
				OrganisationMembershipRole.assign(
						membership,
						OrganisationRole.ORGANIZATION_ADMIN,
						actorUserId,
						occurredAt));
		OrganisationAuditEvent audit = auditRepository.save(
				OrganisationAuditEvent.administratorAssigned(
						membership,
						actorUserId,
						requestId,
						occurredAt));
		outboxRepository.save(OrganisationOutboxEvent.pending(
				audit,
				eventMapper.toPayload(
						membership,
						role.getRole(),
						audit)));
		return membershipMapper.toResponse(membership, List.of(role));
	}

	@Transactional(readOnly = true)
	public OrganisationMembershipPageResponse listAdministrators(
			UUID organisationId,
			int page,
			int size) {
		validatePage(page, size);
		if (!organisationRepository.existsById(organisationId)) {
			throw new OrganisationNotFoundException();
		}
		Page<OrganisationMembership> memberships =
				membershipRepository.findAllByOrganisationIdAndActiveRole(
						organisationId,
						OrganisationRole.ORGANIZATION_ADMIN,
						PageRequest.of(
								page,
								size,
								Sort.by(
										Sort.Order.asc("displayNameSnapshot"),
										Sort.Order.asc("id"))));
		Map<UUID, List<OrganisationMembershipRole>> roles = roleRepository
				.findAllByMembershipIdInAndActiveTrue(
						memberships.getContent().stream()
								.map(OrganisationMembership::getId)
								.toList())
				.stream()
				.collect(Collectors.groupingBy(
						OrganisationMembershipRole::getMembershipId));
		return new OrganisationMembershipPageResponse(
				memberships.getContent().stream()
						.map(membership -> membershipMapper.toResponse(
								membership,
								roles.getOrDefault(
										membership.getId(),
										List.of())))
						.toList(),
				memberships.getNumber(),
				memberships.getSize(),
				memberships.getTotalElements(),
				memberships.getTotalPages());
	}

	@Transactional(readOnly = true)
	public OrganisationMembershipResponse findAdministrator(
			UUID organisationId,
			UUID membershipId) {
		OrganisationMembership membership = membershipRepository
				.findByIdAndOrganisationId(membershipId, organisationId)
				.orElseThrow(OrganisationMembershipNotFoundException::new);
		if (!roleRepository.existsByMembershipIdAndRoleAndActiveTrue(
				membershipId,
				OrganisationRole.ORGANIZATION_ADMIN)) {
			throw new OrganisationMembershipNotFoundException();
		}
		return membershipMapper.toResponse(
				membership,
				roleRepository.findAllByMembershipIdAndActiveTrue(membershipId));
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException(
					"page must be non-negative and size must be between 1 and 100");
		}
	}
}
