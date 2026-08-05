package com.sahha.organisation.mapper;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sahha.organisation.dto.response.OrganisationMembershipResponse;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipRole;
import com.sahha.organisation.entity.OrganisationRole;

@Component
public class OrganisationMembershipMapper {

	public OrganisationMembershipResponse toResponse(
			OrganisationMembership membership,
			Collection<OrganisationMembershipRole> assignments) {
		EnumSet<OrganisationRole> roles = EnumSet.noneOf(OrganisationRole.class);
		assignments.stream()
				.filter(OrganisationMembershipRole::isActive)
				.map(OrganisationMembershipRole::getRole)
				.forEach(roles::add);
		return new OrganisationMembershipResponse(
				membership.getId(),
				membership.getOrganisationId(),
				membership.getUserId(),
				membership.getEmailSnapshot(),
				membership.getDisplayNameSnapshot(),
				membership.getStatus(),
				Set.copyOf(roles),
				membership.getJoinedAt(),
				membership.getCreatedBy(),
				membership.getCreatedAt(),
				membership.getUpdatedAt(),
				membership.getVersion());
	}
}
