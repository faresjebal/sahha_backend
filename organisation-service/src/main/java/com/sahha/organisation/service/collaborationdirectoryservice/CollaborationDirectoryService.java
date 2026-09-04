package com.sahha.organisation.service.collaborationdirectoryservice;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.dto.response.CollaborationDoctorPageResponse;
import com.sahha.organisation.dto.response.OrganisationContextResponse;
import com.sahha.organisation.dto.response.SchedulingDoctorResponse;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipStatus;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.exception.OrganisationContextNotFoundException;
import com.sahha.organisation.exception.StaffMemberNotFoundException;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationContextService;

@Service
public class CollaborationDirectoryService {

	private final OrganisationContextService contextService;
	private final OrganisationMembershipRepository membershipRepository;
	private final OrganisationMembershipRoleRepository roleRepository;

	public CollaborationDirectoryService(
			OrganisationContextService contextService,
			OrganisationMembershipRepository membershipRepository,
			OrganisationMembershipRoleRepository roleRepository) {
		this.contextService = contextService;
		this.membershipRepository = membershipRepository;
		this.roleRepository = roleRepository;
	}

	@Transactional(readOnly = true)
	public CollaborationDoctorPageResponse list(
			UUID organisationId, UUID actorUserId, int page, int size) {
		requireDoctorContext(organisationId, actorUserId);
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("invalid collaboration directory page");
		}
		Page<OrganisationMembership> doctors = membershipRepository
				.findActiveCollaborationDoctors(
						organisationId,
						OrganisationMembershipStatus.ACTIVE,
						OrganisationRole.DOCTOR,
						PageRequest.of(page, size));
		return new CollaborationDoctorPageResponse(
				doctors.map(CollaborationDirectoryService::response).getContent(),
				doctors.getNumber(), doctors.getSize(), doctors.getTotalElements(),
				doctors.getTotalPages());
	}

	@Transactional(readOnly = true)
	public SchedulingDoctorResponse find(
			UUID organisationId, UUID doctorUserId, UUID actorUserId) {
		requireDoctorContext(organisationId, actorUserId);
		OrganisationMembership doctor = membershipRepository
				.findByOrganisationIdAndUserId(organisationId, doctorUserId)
				.filter(candidate -> candidate.getStatus()
						== OrganisationMembershipStatus.ACTIVE)
				.orElseThrow(StaffMemberNotFoundException::new);
		if (!roleRepository.existsByMembershipIdAndRoleAndActiveTrue(
				doctor.getId(), OrganisationRole.DOCTOR)) {
			throw new StaffMemberNotFoundException();
		}
		return response(doctor);
	}

	private void requireDoctorContext(UUID organisationId, UUID actorUserId) {
		OrganisationContextResponse context;
		try {
			context = contextService.resolve(organisationId, actorUserId);
		}
		catch (OrganisationContextNotFoundException missing) {
			throw new OrganisationAccessDeniedException();
		}
		if (!context.roles().contains(OrganisationRole.DOCTOR)) {
			throw new OrganisationAccessDeniedException();
		}
	}

	private static SchedulingDoctorResponse response(OrganisationMembership value) {
		return new SchedulingDoctorResponse(
				value.getId(), value.getOrganisationId(), value.getUserId(),
				value.getDisplayNameSnapshot(), value.getVersion());
	}
}
