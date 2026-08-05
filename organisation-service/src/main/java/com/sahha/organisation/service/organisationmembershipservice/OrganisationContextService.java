package com.sahha.organisation.service.organisationmembershipservice;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.dto.response.OrganisationContextResponse;
import com.sahha.organisation.entity.Organisation;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipRole;
import com.sahha.organisation.entity.OrganisationMembershipStatus;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.OrganisationStatus;
import com.sahha.organisation.exception.OrganisationContextNotFoundException;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.repository.OrganisationRepository;

@Service
public class OrganisationContextService {

	private final OrganisationRepository organisationRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final OrganisationMembershipRoleRepository roleRepository;

	public OrganisationContextService(
			OrganisationRepository organisationRepository,
			OrganisationMembershipRepository membershipRepository,
			OrganisationMembershipRoleRepository roleRepository) {
		this.organisationRepository = organisationRepository;
		this.membershipRepository = membershipRepository;
		this.roleRepository = roleRepository;
	}

	@Transactional(readOnly = true)
	public List<OrganisationContextResponse> listAvailable(UUID userId) {
		List<OrganisationMembership> memberships = membershipRepository
				.findAllByUserIdAndStatus(
						userId,
						OrganisationMembershipStatus.ACTIVE);
		if (memberships.isEmpty()) {
			return List.of();
		}
		Map<UUID, Organisation> organisations = organisationRepository
				.findAllById(memberships.stream()
						.map(OrganisationMembership::getOrganisationId)
						.toList())
				.stream()
				.filter(organisation ->
						organisation.getStatus() == OrganisationStatus.ACTIVE)
				.collect(Collectors.toMap(
						Organisation::getId,
						Function.identity()));
		Map<UUID, Set<OrganisationRole>> rolesByMembership = roleRepository
				.findAllByMembershipIdInAndActiveTrue(memberships.stream()
						.map(OrganisationMembership::getId)
						.toList())
				.stream()
				.collect(Collectors.groupingBy(
						OrganisationMembershipRole::getMembershipId,
						Collectors.mapping(
								OrganisationMembershipRole::getRole,
								Collectors.toUnmodifiableSet())));
		return memberships.stream()
				.filter(membership -> organisations.containsKey(
						membership.getOrganisationId()))
				.filter(membership -> !rolesByMembership
						.getOrDefault(membership.getId(), Set.of())
						.isEmpty())
				.map(membership -> response(
						membership,
						organisations.get(membership.getOrganisationId()),
						rolesByMembership.get(membership.getId())))
				.sorted(Comparator
						.comparing(OrganisationContextResponse::organisationName)
						.thenComparing(OrganisationContextResponse::organisationId))
				.toList();
	}

	@Transactional(readOnly = true)
	public OrganisationContextResponse resolve(
			UUID organisationId,
			UUID userId) {
		OrganisationMembership membership = membershipRepository
				.findByOrganisationIdAndUserId(organisationId, userId)
				.filter(candidate -> candidate.getStatus()
						== OrganisationMembershipStatus.ACTIVE)
				.orElseThrow(OrganisationContextNotFoundException::new);
		Organisation organisation = organisationRepository.findById(organisationId)
				.filter(candidate -> candidate.getStatus()
						== OrganisationStatus.ACTIVE)
				.orElseThrow(OrganisationContextNotFoundException::new);
		Set<OrganisationRole> roles = roleRepository
				.findAllByMembershipIdAndActiveTrue(membership.getId())
				.stream()
				.map(OrganisationMembershipRole::getRole)
				.collect(Collectors.toUnmodifiableSet());
		if (roles.isEmpty()) {
			throw new OrganisationContextNotFoundException();
		}
		return response(membership, organisation, roles);
	}

	@Transactional(readOnly = true)
	public void requireActiveRole(
			UUID organisationId,
			UUID userId,
			OrganisationRole requiredRole) {
		try {
			OrganisationContextResponse context = resolve(
					organisationId,
					userId);
			if (!context.roles().contains(requiredRole)) {
				throw new OrganisationAccessDeniedException();
			}
		}
		catch (OrganisationContextNotFoundException notActive) {
			throw new OrganisationAccessDeniedException();
		}
	}

	private static OrganisationContextResponse response(
			OrganisationMembership membership,
			Organisation organisation,
			Set<OrganisationRole> roles) {
		return new OrganisationContextResponse(
				membership.getId(),
				organisation.getId(),
				organisation.getName(),
				organisation.getType(),
				Set.copyOf(roles),
				membership.getVersion());
	}
}
