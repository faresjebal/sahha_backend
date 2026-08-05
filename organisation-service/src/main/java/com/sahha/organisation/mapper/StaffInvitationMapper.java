package com.sahha.organisation.mapper;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.sahha.organisation.dto.response.StaffInvitationResponse;
import com.sahha.organisation.entity.StaffInvitation;

@Component
public class StaffInvitationMapper {

	public StaffInvitationResponse toResponse(
			StaffInvitation invitation,
			String organisationName,
			Instant observedAt) {
		return new StaffInvitationResponse(
				invitation.getId(),
				invitation.getOrganisationId(),
				organisationName,
				invitation.getEmail(),
				invitation.getRole(),
				invitation.statusAt(observedAt),
				invitation.getExpiresAt(),
				invitation.getResolvedAt(),
				invitation.getResolvedByUserId(),
				invitation.getAcceptedMembershipId(),
				invitation.getCreatedBy(),
				invitation.getCreatedAt(),
				invitation.getUpdatedAt(),
				invitation.getVersion());
	}
}
