package com.sahha.organisation.service.schedulingdirectoryservice;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.dto.response.OrganisationContextResponse;
import com.sahha.organisation.dto.response.SchedulingDoctorResponse;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipStatus;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.OrganisationStatus;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.exception.OrganisationContextNotFoundException;
import com.sahha.organisation.exception.StaffMemberNotFoundException;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.repository.OrganisationRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationContextService;

@Service
public class SchedulingDirectoryService {

	private static final Set<OrganisationRole> SCHEDULING_ROLES = Set.of(
			OrganisationRole.ORGANIZATION_ADMIN,
			OrganisationRole.RECEPTIONIST,
			OrganisationRole.DOCTOR);

	private final OrganisationContextService contextService;
	private final OrganisationRepository organisationRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final OrganisationMembershipRoleRepository roleRepository;

	public SchedulingDirectoryService(
			OrganisationContextService contextService,
			OrganisationRepository organisationRepository,
			OrganisationMembershipRepository membershipRepository,
			OrganisationMembershipRoleRepository roleRepository) {
		this.contextService = contextService;
		this.organisationRepository = organisationRepository;
		this.membershipRepository = membershipRepository;
		this.roleRepository = roleRepository;
	}

	@Transactional(readOnly = true)
	public SchedulingDoctorResponse findPatientVisibleActiveDoctor(
			UUID organisationId,
			UUID doctorUserId) {
		organisationRepository.findById(organisationId)
				.filter(organisation -> organisation.getStatus()
						== OrganisationStatus.ACTIVE)
				.orElseThrow(StaffMemberNotFoundException::new);
		return activeDoctor(organisationId, doctorUserId);
	}

	@Transactional(readOnly = true)
	public SchedulingDoctorResponse findActiveDoctor(
			UUID organisationId,
			UUID doctorUserId,
			UUID actorUserId) {
		OrganisationContextResponse actorContext;
		try {
			actorContext = contextService.resolve(organisationId, actorUserId);
		}
		catch (OrganisationContextNotFoundException unavailableContext) {
			throw new OrganisationAccessDeniedException();
		}
		if (actorContext.roles().stream().noneMatch(SCHEDULING_ROLES::contains)) {
			throw new OrganisationAccessDeniedException();
		}
		boolean operational = actorContext.roles().contains(
				OrganisationRole.ORGANIZATION_ADMIN)
				|| actorContext.roles().contains(OrganisationRole.RECEPTIONIST);
		if (!operational && !actorUserId.equals(doctorUserId)) {
			throw new OrganisationAccessDeniedException();
		}
		return activeDoctor(organisationId, doctorUserId);
	}

	private SchedulingDoctorResponse activeDoctor(
			UUID organisationId,
			UUID doctorUserId) {
		OrganisationMembership doctor = membershipRepository
				.findByOrganisationIdAndUserId(organisationId, doctorUserId)
				.filter(candidate -> candidate.getStatus()
						== OrganisationMembershipStatus.ACTIVE)
				.orElseThrow(StaffMemberNotFoundException::new);
		if (!roleRepository.existsByMembershipIdAndRoleAndActiveTrue(
				doctor.getId(), OrganisationRole.DOCTOR)) {
			throw new StaffMemberNotFoundException();
		}
		return new SchedulingDoctorResponse(
				doctor.getId(),
				doctor.getOrganisationId(),
				doctor.getUserId(),
				doctor.getDisplayNameSnapshot(),
				doctor.getVersion());
	}
}
