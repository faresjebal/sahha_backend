package com.sahha.organisation.service.staffdirectoryservice;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import com.sahha.organisation.dto.request.ChangeStaffMembershipStatusRequest;
import com.sahha.organisation.dto.request.CreateStaffDepartmentAssignmentRequest;
import com.sahha.organisation.dto.request.EndStaffDepartmentAssignmentRequest;
import com.sahha.organisation.dto.request.UpsertDoctorProfileRequest;
import com.sahha.organisation.dto.response.DoctorProfileResponse;
import com.sahha.organisation.dto.response.StaffDepartmentAssignmentResponse;
import com.sahha.organisation.dto.response.StaffMemberPageResponse;
import com.sahha.organisation.dto.response.StaffMemberResponse;
import com.sahha.organisation.entity.Department;
import com.sahha.organisation.entity.DepartmentStatus;
import com.sahha.organisation.entity.DoctorProfile;
import com.sahha.organisation.entity.OrganisationAuditEvent;
import com.sahha.organisation.entity.OrganisationAuditEventType;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipRole;
import com.sahha.organisation.entity.OrganisationMembershipStatus;
import com.sahha.organisation.entity.OrganisationOutboxEvent;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.StaffDepartmentAssignment;
import com.sahha.organisation.entity.StaffDepartmentAssignmentStatus;
import com.sahha.organisation.event.OrganisationEventMapper;
import com.sahha.organisation.exception.ConcurrentStaffResourceModificationException;
import com.sahha.organisation.exception.DepartmentNotFoundException;
import com.sahha.organisation.exception.DoctorProfileNotFoundException;
import com.sahha.organisation.exception.StaffDepartmentAssignmentNotFoundException;
import com.sahha.organisation.exception.StaffManagementConflictException;
import com.sahha.organisation.exception.StaffMemberNotFoundException;
import com.sahha.organisation.mapper.StaffDirectoryMapper;
import com.sahha.organisation.repository.DepartmentRepository;
import com.sahha.organisation.repository.DoctorProfileRepository;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.repository.StaffDepartmentAssignmentRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationContextService;

@Service
public class StaffDirectoryService {

	private static final Set<OrganisationRole> STAFF_ROLES =
			EnumSet.of(OrganisationRole.DOCTOR, OrganisationRole.RECEPTIONIST);

	private final OrganisationMembershipRepository membershipRepository;
	private final OrganisationMembershipRoleRepository roleRepository;
	private final DepartmentRepository departmentRepository;
	private final StaffDepartmentAssignmentRepository assignmentRepository;
	private final DoctorProfileRepository doctorProfileRepository;
	private final OrganisationAuditEventRepository auditRepository;
	private final OrganisationOutboxEventRepository outboxRepository;
	private final OrganisationContextService contextService;
	private final StaffDirectoryMapper mapper;
	private final OrganisationEventMapper eventMapper;
	private final Clock clock;

	public StaffDirectoryService(
			OrganisationMembershipRepository membershipRepository,
			OrganisationMembershipRoleRepository roleRepository,
			DepartmentRepository departmentRepository,
			StaffDepartmentAssignmentRepository assignmentRepository,
			DoctorProfileRepository doctorProfileRepository,
			OrganisationAuditEventRepository auditRepository,
			OrganisationOutboxEventRepository outboxRepository,
			OrganisationContextService contextService,
			StaffDirectoryMapper mapper,
			OrganisationEventMapper eventMapper,
			Clock clock) {
		this.membershipRepository = membershipRepository;
		this.roleRepository = roleRepository;
		this.departmentRepository = departmentRepository;
		this.assignmentRepository = assignmentRepository;
		this.doctorProfileRepository = doctorProfileRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.contextService = contextService;
		this.mapper = mapper;
		this.eventMapper = eventMapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public StaffMemberPageResponse list(
			UUID organisationId,
			UUID actorUserId,
			int page,
			int size) {
		requireAdministrator(organisationId, actorUserId);
		validatePage(page, size);
		Page<OrganisationMembership> memberships = membershipRepository
				.findAllStaffByOrganisationId(
						organisationId,
						STAFF_ROLES,
						PageRequest.of(
								page,
								size,
								Sort.by(
										Sort.Order.asc("displayNameSnapshot"),
										Sort.Order.asc("id"))));
		List<StaffMemberResponse> items = mapMembers(memberships.getContent());
		return new StaffMemberPageResponse(
				items,
				memberships.getNumber(),
				memberships.getSize(),
				memberships.getTotalElements(),
				memberships.getTotalPages());
	}

	@Transactional(readOnly = true)
	public StaffMemberResponse find(
			UUID organisationId,
			UUID membershipId,
			UUID actorUserId) {
		requireAdministrator(organisationId, actorUserId);
		return mapMembers(List.of(findStaff(organisationId, membershipId)))
				.getFirst();
	}

	@Transactional
	public StaffMemberResponse changeStatus(
			UUID organisationId,
			UUID membershipId,
			ChangeStaffMembershipStatusRequest request,
			UUID actorUserId,
			String requestId) {
		requireAdministrator(organisationId, actorUserId);
		OrganisationMembership membership = findStaff(
				organisationId,
				membershipId);
		requireVersion(membership.getVersion(), request.version());
		Instant occurredAt = clock.instant();
		try {
			if (membership.changeStatus(
					request.status(),
					actorUserId,
					occurredAt)) {
				List<StaffDepartmentAssignment> endedAssignments = List.of();
				if (request.status() == OrganisationMembershipStatus.REMOVED) {
					endedAssignments = deactivateStaffAccess(
							membership,
							occurredAt);
				}
				membershipRepository.saveAndFlush(membership);
				recordMembershipChange(
						membership,
						actorUserId,
						requestId,
						occurredAt);
				for (StaffDepartmentAssignment assignment : endedAssignments) {
					recordAssignmentChange(
							assignment,
							membership,
							OrganisationAuditEventType
									.STAFF_DEPARTMENT_ASSIGNMENT_ENDED,
							actorUserId,
							requestId,
							occurredAt);
				}
			}
		}
		catch (IllegalStateException invalidTransition) {
			throw new StaffManagementConflictException();
		}
		catch (ObjectOptimisticLockingFailureException concurrentChange) {
			throw new ConcurrentStaffResourceModificationException();
		}
		return mapMembers(List.of(membership)).getFirst();
	}

	@Transactional
	public StaffDepartmentAssignmentResponse assignDepartment(
			UUID organisationId,
			UUID membershipId,
			CreateStaffDepartmentAssignmentRequest request,
			UUID actorUserId,
			String requestId) {
		requireAdministrator(organisationId, actorUserId);
		OrganisationMembership membership = findStaff(
				organisationId,
				membershipId);
		if (membership.getStatus() != OrganisationMembershipStatus.ACTIVE
				|| !hasActiveStaffRole(membershipId)) {
			throw new StaffManagementConflictException();
		}
		Department department = departmentRepository
				.findByIdAndOrganisationId(
						request.departmentId(),
						organisationId)
				.filter(candidate -> candidate.getStatus() == DepartmentStatus.ACTIVE)
				.orElseThrow(DepartmentNotFoundException::new);
		Instant occurredAt = clock.instant();
		StaffDepartmentAssignment assignment =
				StaffDepartmentAssignment.assign(
						organisationId,
						membershipId,
						department.getId(),
						request.positionTitle(),
						request.primaryAssignment(),
						request.startDate(),
						request.plannedEndDate(),
						actorUserId,
						occurredAt);
		try {
			assignmentRepository.saveAndFlush(assignment);
		}
		catch (DataIntegrityViolationException conflict) {
			throw new StaffManagementConflictException();
		}
		recordAssignmentChange(
				assignment,
				membership,
				OrganisationAuditEventType.STAFF_DEPARTMENT_ASSIGNED,
				actorUserId,
				requestId,
				occurredAt);
		return mapper.assignmentResponse(assignment, department);
	}

	@Transactional
	public StaffDepartmentAssignmentResponse endDepartmentAssignment(
			UUID organisationId,
			UUID membershipId,
			UUID assignmentId,
			EndStaffDepartmentAssignmentRequest request,
			UUID actorUserId,
			String requestId) {
		requireAdministrator(organisationId, actorUserId);
		OrganisationMembership membership = findStaff(
				organisationId,
				membershipId);
		StaffDepartmentAssignment assignment = assignmentRepository
				.findByIdAndMembershipIdAndOrganisationId(
						assignmentId,
						membershipId,
						organisationId)
				.orElseThrow(StaffDepartmentAssignmentNotFoundException::new);
		requireVersion(assignment.getVersion(), request.version());
		Instant occurredAt = clock.instant();
		try {
			if (assignment.end(
					request.endDate(),
					actorUserId,
					occurredAt)) {
				assignmentRepository.saveAndFlush(assignment);
				recordAssignmentChange(
						assignment,
						membership,
						OrganisationAuditEventType
								.STAFF_DEPARTMENT_ASSIGNMENT_ENDED,
						actorUserId,
						requestId,
						occurredAt);
			}
		}
		catch (ObjectOptimisticLockingFailureException concurrentChange) {
			throw new ConcurrentStaffResourceModificationException();
		}
		Department department = departmentRepository
				.findByIdAndOrganisationId(
						assignment.getDepartmentId(),
						organisationId)
				.orElseThrow(DepartmentNotFoundException::new);
		return mapper.assignmentResponse(assignment, department);
	}

	@Transactional(readOnly = true)
	public DoctorProfileResponse findDoctorProfileForAdministrator(
			UUID organisationId,
			UUID membershipId,
			UUID actorUserId) {
		requireAdministrator(organisationId, actorUserId);
		OrganisationMembership membership = findStaff(
				organisationId,
				membershipId);
		requireDoctorRole(membership.getId(), false);
		return mapper.doctorProfileResponse(doctorProfileRepository
				.findByMembershipIdAndOrganisationId(
						membershipId,
						organisationId)
				.orElseThrow(DoctorProfileNotFoundException::new));
	}

	@Transactional(readOnly = true)
	public DoctorProfileResponse findMyDoctorProfile(
			UUID organisationId,
			UUID actorUserId) {
		OrganisationMembership membership = requireActiveDoctor(
				organisationId,
				actorUserId);
		return mapper.doctorProfileResponse(doctorProfileRepository
				.findByMembershipIdAndOrganisationId(
						membership.getId(),
						organisationId)
				.orElseThrow(DoctorProfileNotFoundException::new));
	}

	@Transactional
	public DoctorProfileResponse upsertMyDoctorProfile(
			UUID organisationId,
			UUID actorUserId,
			UpsertDoctorProfileRequest request,
			String requestId) {
		OrganisationMembership membership = requireActiveDoctor(
				organisationId,
				actorUserId);
		Instant occurredAt = clock.instant();
		DoctorProfile profile = doctorProfileRepository
				.findByMembershipIdAndOrganisationId(
						membership.getId(),
						organisationId)
				.orElse(null);
		OrganisationAuditEventType eventType;
		try {
			if (profile == null) {
				if (request.version() != null) {
					throw new ConcurrentStaffResourceModificationException();
				}
				profile = DoctorProfile.create(
						organisationId,
						membership.getId(),
						request.specialty(),
						request.professionalTitle(),
						request.licenceNumber(),
						request.registrationAuthority(),
						request.biography(),
						actorUserId,
						occurredAt);
				eventType = OrganisationAuditEventType.DOCTOR_PROFILE_CREATED;
			}
			else {
				if (request.version() == null) {
					throw new ConcurrentStaffResourceModificationException();
				}
				requireVersion(profile.getVersion(), request.version());
				profile.update(
						request.specialty(),
						request.professionalTitle(),
						request.licenceNumber(),
						request.registrationAuthority(),
						request.biography(),
						actorUserId,
						occurredAt);
				eventType = OrganisationAuditEventType.DOCTOR_PROFILE_UPDATED;
			}
			doctorProfileRepository.saveAndFlush(profile);
		}
		catch (DataIntegrityViolationException conflict) {
			throw new StaffManagementConflictException();
		}
		catch (ObjectOptimisticLockingFailureException concurrentChange) {
			throw new ConcurrentStaffResourceModificationException();
		}
		recordDoctorProfileChange(
				profile,
				membership,
				eventType,
				actorUserId,
				requestId,
				occurredAt);
		return mapper.doctorProfileResponse(profile);
	}

	private OrganisationMembership requireActiveDoctor(
			UUID organisationId,
			UUID actorUserId) {
		contextService.requireActiveRole(
				organisationId,
				actorUserId,
				OrganisationRole.DOCTOR);
		OrganisationMembership membership = membershipRepository
				.findByOrganisationIdAndUserId(organisationId, actorUserId)
				.filter(candidate -> candidate.getStatus()
						== OrganisationMembershipStatus.ACTIVE)
				.orElseThrow(StaffMemberNotFoundException::new);
		requireDoctorRole(membership.getId(), true);
		return membership;
	}

	private OrganisationMembership findStaff(
			UUID organisationId,
			UUID membershipId) {
		OrganisationMembership membership = membershipRepository
				.findByIdAndOrganisationId(membershipId, organisationId)
				.orElseThrow(StaffMemberNotFoundException::new);
		List<OrganisationMembershipRole> roles = roleRepository
				.findAllByMembershipId(membershipId);
		boolean staff = roles.stream()
				.map(OrganisationMembershipRole::getRole)
				.anyMatch(STAFF_ROLES::contains);
		boolean administrator = roles.stream()
				.anyMatch(role -> role.isActive()
						&& role.getRole() == OrganisationRole.ORGANIZATION_ADMIN);
		if (!staff || administrator) {
			throw new StaffMemberNotFoundException();
		}
		return membership;
	}

	private boolean hasActiveStaffRole(UUID membershipId) {
		return roleRepository.findAllByMembershipIdAndActiveTrue(membershipId)
				.stream()
				.map(OrganisationMembershipRole::getRole)
				.anyMatch(STAFF_ROLES::contains);
	}

	private void requireDoctorRole(UUID membershipId, boolean activeOnly) {
		boolean doctor = (activeOnly
				? roleRepository.findAllByMembershipIdAndActiveTrue(membershipId)
				: roleRepository.findAllByMembershipId(membershipId))
				.stream()
				.anyMatch(role -> role.getRole() == OrganisationRole.DOCTOR);
		if (!doctor) {
			throw new StaffMemberNotFoundException();
		}
	}

	private List<StaffDepartmentAssignment> deactivateStaffAccess(
			OrganisationMembership membership,
			Instant occurredAt) {
		roleRepository.findAllByMembershipIdAndActiveTrue(membership.getId())
				.stream()
				.filter(role -> STAFF_ROLES.contains(role.getRole()))
				.forEach(role -> role.deactivate(occurredAt));
		LocalDate occurredDate = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
		List<StaffDepartmentAssignment> activeAssignments = assignmentRepository
				.findAllByMembershipIdAndStatus(
				membership.getId(),
				StaffDepartmentAssignmentStatus.ACTIVE);
		activeAssignments
				.forEach(assignment -> assignment.end(
						occurredDate.isBefore(assignment.getStartDate())
								? assignment.getStartDate()
								: occurredDate,
						membership.getUpdatedBy(),
						occurredAt));
		return activeAssignments;
	}

	private List<StaffMemberResponse> mapMembers(
			List<OrganisationMembership> memberships) {
		if (memberships.isEmpty()) {
			return List.of();
		}
		List<UUID> membershipIds = memberships.stream()
				.map(OrganisationMembership::getId)
				.toList();
		Map<UUID, List<OrganisationMembershipRole>> roles = roleRepository
				.findAllByMembershipIdIn(membershipIds)
				.stream()
				.collect(Collectors.groupingBy(
						OrganisationMembershipRole::getMembershipId));
		Map<UUID, List<StaffDepartmentAssignment>> assignments =
				assignmentRepository
						.findAllByMembershipIdInOrderByCreatedAtDesc(membershipIds)
						.stream()
						.collect(Collectors.groupingBy(
								StaffDepartmentAssignment::getMembershipId));
		Map<UUID, Department> departments = departmentRepository
				.findAllById(assignments.values().stream()
						.flatMap(List::stream)
						.map(StaffDepartmentAssignment::getDepartmentId)
						.distinct()
						.toList())
				.stream()
				.collect(Collectors.toUnmodifiableMap(
						Department::getId,
						Function.identity()));
		Map<UUID, DoctorProfile> profiles = doctorProfileRepository
				.findAllByMembershipIdIn(membershipIds)
				.stream()
				.collect(Collectors.toUnmodifiableMap(
						DoctorProfile::getMembershipId,
						Function.identity()));
		return memberships.stream()
				.map(membership -> mapper.toResponse(
						membership,
						roles.getOrDefault(membership.getId(), List.of()),
						assignments.getOrDefault(
								membership.getId(),
								List.of()),
						departments,
						profiles.get(membership.getId())))
				.toList();
	}

	private void recordMembershipChange(
			OrganisationMembership membership,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		OrganisationAuditEvent audit = auditRepository.save(
				OrganisationAuditEvent.staffMembershipChanged(
						membership,
						actorUserId,
						requestId,
						occurredAt));
		outboxRepository.save(OrganisationOutboxEvent.pending(
				audit,
				eventMapper.toPayload(membership, audit)));
	}

	private void recordAssignmentChange(
			StaffDepartmentAssignment assignment,
			OrganisationMembership membership,
			OrganisationAuditEventType eventType,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		OrganisationAuditEvent audit = auditRepository.save(
				OrganisationAuditEvent.staffDepartmentAssignmentChanged(
						assignment,
						membership.getUserId(),
						eventType,
						actorUserId,
						requestId,
						occurredAt));
		outboxRepository.save(OrganisationOutboxEvent.pending(
				audit,
				eventMapper.toPayload(
						assignment,
						membership.getUserId(),
						audit)));
	}

	private void recordDoctorProfileChange(
			DoctorProfile profile,
			OrganisationMembership membership,
			OrganisationAuditEventType eventType,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		OrganisationAuditEvent audit = auditRepository.save(
				OrganisationAuditEvent.doctorProfileChanged(
						profile,
						membership.getUserId(),
						eventType,
						actorUserId,
						requestId,
						occurredAt));
		outboxRepository.save(OrganisationOutboxEvent.pending(
				audit,
				eventMapper.toPayload(
						profile,
						membership.getUserId(),
						audit)));
	}

	private void requireAdministrator(UUID organisationId, UUID actorUserId) {
		contextService.requireActiveRole(
				organisationId,
				actorUserId,
				OrganisationRole.ORGANIZATION_ADMIN);
	}

	private static void requireVersion(long current, long requested) {
		if (current != requested) {
			throw new ConcurrentStaffResourceModificationException();
		}
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException(
					"page must be non-negative and size must be between 1 and 100");
		}
	}
}
