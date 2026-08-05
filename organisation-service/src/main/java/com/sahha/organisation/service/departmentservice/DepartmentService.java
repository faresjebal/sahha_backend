package com.sahha.organisation.service.departmentservice;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.dto.request.ChangeDepartmentStatusRequest;
import com.sahha.organisation.dto.request.CreateDepartmentRequest;
import com.sahha.organisation.dto.request.UpdateDepartmentRequest;
import com.sahha.organisation.dto.response.DepartmentPageResponse;
import com.sahha.organisation.dto.response.DepartmentResponse;
import com.sahha.organisation.entity.Department;
import com.sahha.organisation.entity.OrganisationAuditEvent;
import com.sahha.organisation.entity.OrganisationAuditEventType;
import com.sahha.organisation.entity.OrganisationOutboxEvent;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.event.OrganisationEventMapper;
import com.sahha.organisation.exception.ConcurrentDepartmentModificationException;
import com.sahha.organisation.exception.DepartmentConflictException;
import com.sahha.organisation.exception.DepartmentNotFoundException;
import com.sahha.organisation.mapper.DepartmentMapper;
import com.sahha.organisation.repository.DepartmentRepository;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationContextService;

@Service
public class DepartmentService {

	private final DepartmentRepository departmentRepository;
	private final OrganisationAuditEventRepository auditRepository;
	private final OrganisationOutboxEventRepository outboxRepository;
	private final OrganisationContextService contextService;
	private final DepartmentMapper departmentMapper;
	private final OrganisationEventMapper eventMapper;
	private final Clock clock;

	public DepartmentService(
			DepartmentRepository departmentRepository,
			OrganisationAuditEventRepository auditRepository,
			OrganisationOutboxEventRepository outboxRepository,
			OrganisationContextService contextService,
			DepartmentMapper departmentMapper,
			OrganisationEventMapper eventMapper,
			Clock clock) {
		this.departmentRepository = departmentRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.contextService = contextService;
		this.departmentMapper = departmentMapper;
		this.eventMapper = eventMapper;
		this.clock = clock;
	}

	@Transactional
	public DepartmentResponse create(
			UUID organisationId,
			CreateDepartmentRequest request,
			UUID actorUserId,
			String requestId) {
		requireAdministrator(organisationId, actorUserId);
		Instant occurredAt = clock.instant();
		Department department = Department.create(
				organisationId,
				request.name(),
				request.code(),
				request.description(),
				actorUserId,
				occurredAt);
		try {
			departmentRepository.saveAndFlush(department);
		}
		catch (DataIntegrityViolationException conflict) {
			throw new DepartmentConflictException();
		}
		recordChange(
				department,
				OrganisationAuditEventType.DEPARTMENT_CREATED,
				actorUserId,
				requestId,
				occurredAt);
		return departmentMapper.toResponse(department);
	}

	@Transactional(readOnly = true)
	public DepartmentPageResponse list(
			UUID organisationId,
			UUID actorUserId,
			int page,
			int size) {
		requireAdministrator(organisationId, actorUserId);
		validatePage(page, size);
		Page<Department> departments = departmentRepository
				.findAllByOrganisationId(
						organisationId,
						PageRequest.of(
								page,
								size,
								Sort.by(
										Sort.Order.asc("normalizedName"),
										Sort.Order.asc("id"))));
		return new DepartmentPageResponse(
				departments.getContent().stream()
						.map(departmentMapper::toResponse)
						.toList(),
				departments.getNumber(),
				departments.getSize(),
				departments.getTotalElements(),
				departments.getTotalPages());
	}

	@Transactional(readOnly = true)
	public DepartmentResponse find(
			UUID organisationId,
			UUID departmentId,
			UUID actorUserId) {
		requireAdministrator(organisationId, actorUserId);
		return departmentMapper.toResponse(findScoped(
				organisationId,
				departmentId));
	}

	@Transactional
	public DepartmentResponse update(
			UUID organisationId,
			UUID departmentId,
			UpdateDepartmentRequest request,
			UUID actorUserId,
			String requestId) {
		requireAdministrator(organisationId, actorUserId);
		Department department = findScoped(organisationId, departmentId);
		requireVersion(department, request.version());
		Instant occurredAt = clock.instant();
		department.updateDetails(
				request.name(),
				request.code(),
				request.description(),
				actorUserId,
				occurredAt);
		flush(department);
		recordChange(
				department,
				OrganisationAuditEventType.DEPARTMENT_UPDATED,
				actorUserId,
				requestId,
				occurredAt);
		return departmentMapper.toResponse(department);
	}

	@Transactional
	public DepartmentResponse changeStatus(
			UUID organisationId,
			UUID departmentId,
			ChangeDepartmentStatusRequest request,
			UUID actorUserId,
			String requestId) {
		requireAdministrator(organisationId, actorUserId);
		Department department = findScoped(organisationId, departmentId);
		requireVersion(department, request.version());
		Instant occurredAt = clock.instant();
		if (department.changeStatus(
				request.status(),
				actorUserId,
				occurredAt)) {
			flush(department);
			recordChange(
					department,
					OrganisationAuditEventType.DEPARTMENT_STATUS_CHANGED,
					actorUserId,
					requestId,
					occurredAt);
		}
		return departmentMapper.toResponse(department);
	}

	private void requireAdministrator(UUID organisationId, UUID actorUserId) {
		contextService.requireActiveRole(
				organisationId,
				actorUserId,
				OrganisationRole.ORGANIZATION_ADMIN);
	}

	private Department findScoped(UUID organisationId, UUID departmentId) {
		return departmentRepository
				.findByIdAndOrganisationId(departmentId, organisationId)
				.orElseThrow(DepartmentNotFoundException::new);
	}

	private void flush(Department department) {
		try {
			departmentRepository.saveAndFlush(department);
		}
		catch (ObjectOptimisticLockingFailureException concurrentChange) {
			throw new ConcurrentDepartmentModificationException();
		}
		catch (DataIntegrityViolationException conflict) {
			throw new DepartmentConflictException();
		}
	}

	private void recordChange(
			Department department,
			OrganisationAuditEventType eventType,
			UUID actorUserId,
			String requestId,
			Instant occurredAt) {
		OrganisationAuditEvent audit = auditRepository.save(
				OrganisationAuditEvent.departmentChanged(
						department,
						eventType,
						actorUserId,
						requestId,
						occurredAt));
		outboxRepository.save(OrganisationOutboxEvent.pending(
				audit,
				eventMapper.toPayload(department, audit)));
	}

	private static void requireVersion(Department department, long version) {
		if (department.getVersion() != version) {
			throw new ConcurrentDepartmentModificationException();
		}
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException(
					"page must be non-negative and size must be between 1 and 100");
		}
	}
}
