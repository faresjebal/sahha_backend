package com.sahha.scheduling.service.appointmentservice;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.client.organisation.SchedulingDoctorResource;
import com.sahha.scheduling.dto.request.AppointmentCommandRequest;
import com.sahha.scheduling.dto.request.ReasonedAppointmentCommandRequest;
import com.sahha.scheduling.dto.request.RescheduleAppointmentRequest;
import com.sahha.scheduling.dto.response.AppointmentResponse;
import com.sahha.scheduling.dto.response.AvailableSlotResponse;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentAuditEvent;
import com.sahha.scheduling.entity.AppointmentAuditEventType;
import com.sahha.scheduling.entity.AppointmentStatus;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;
import com.sahha.scheduling.exception.AppointmentCommandConflictException;
import com.sahha.scheduling.exception.AppointmentDoctorNotFoundException;
import com.sahha.scheduling.exception.AppointmentNotFoundException;
import com.sahha.scheduling.exception.AppointmentSlotUnavailableException;
import com.sahha.scheduling.exception.AvailabilityNotFoundException;
import com.sahha.scheduling.exception.ConcurrentAppointmentModificationException;
import com.sahha.scheduling.exception.InvalidAppointmentTransitionException;
import com.sahha.scheduling.mapper.AppointmentMapper;
import com.sahha.scheduling.repository.AppointmentAuditEventRepository;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.repository.DoctorAvailabilityScheduleRepository;
import com.sahha.scheduling.outbox.AppointmentOutboxRecorder;
import com.sahha.scheduling.service.availabilityservice.SlotCalculationService;

@Service
public class AppointmentTransitionPersistenceService {

	private final AppointmentRepository appointmentRepository;
	private final AppointmentAuditEventRepository auditRepository;
	private final DoctorAvailabilityScheduleRepository availabilityRepository;
	private final AppointmentTransitionPolicy policy;
	private final AppointmentOutboxRecorder outboxRecorder;
	private final SlotCalculationService slotCalculationService;
	private final AppointmentMapper mapper;
	private final Clock clock;

	public AppointmentTransitionPersistenceService(
			AppointmentRepository appointmentRepository,
			AppointmentAuditEventRepository auditRepository,
			DoctorAvailabilityScheduleRepository availabilityRepository,
			AppointmentTransitionPolicy policy,
			AppointmentOutboxRecorder outboxRecorder,
			SlotCalculationService slotCalculationService,
			AppointmentMapper mapper,
			Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.auditRepository = auditRepository;
		this.availabilityRepository = availabilityRepository;
		this.policy = policy;
		this.outboxRecorder = outboxRecorder;
		this.slotCalculationService = slotCalculationService;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AppointmentReschedulePreparation prepareReschedule(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			UUID appointmentId,
			UUID commandRequestId,
			String reason,
			Instant startsAt) {
		Appointment appointment = find(organisationId, appointmentId);
		policy.requireActorCanTransition(
				appointment, actorUserId, actor, AppointmentStatus.RESCHEDULED);
		AppointmentAuditEvent existing = auditRepository
				.findByOrganisationIdAndCommandRequestId(
						organisationId, commandRequestId)
				.orElse(null);
		if (existing != null) {
			if (!existing.matchesCommand(
					appointmentId,
					actorUserId,
					AppointmentAuditEventType.APPOINTMENT_RESCHEDULED,
					reason,
					startsAt)) {
				throw new AppointmentCommandConflictException();
			}
			return new AppointmentReschedulePreparation(
					appointment.getDoctorUserId(), mapper.response(appointment));
		}
		return new AppointmentReschedulePreparation(
				appointment.getDoctorUserId(), null);
	}

	@Transactional
	public AppointmentResponse confirm(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		return transition(
				organisationId, actorUserId, actor, requestId, appointmentId,
				request.commandRequestId(), request.version(),
				AppointmentStatus.CONFIRMED, null, null, null);
	}

	@Transactional
	public AppointmentResponse reject(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			String requestId,
			UUID appointmentId,
			ReasonedAppointmentCommandRequest request) {
		return transition(
				organisationId, actorUserId, actor, requestId, appointmentId,
				request.commandRequestId(), request.version(),
				AppointmentStatus.REJECTED, request.reason(), null, null);
	}

	@Transactional
	public AppointmentResponse cancel(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			String requestId,
			UUID appointmentId,
			ReasonedAppointmentCommandRequest request) {
		return transition(
				organisationId, actorUserId, actor, requestId, appointmentId,
				request.commandRequestId(), request.version(),
				AppointmentStatus.CANCELLED, request.reason(), null, null);
	}

	@Transactional
	public AppointmentResponse checkIn(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		return transition(
				organisationId, actorUserId, actor, requestId, appointmentId,
				request.commandRequestId(), request.version(),
				AppointmentStatus.CHECKED_IN, null, null, null);
	}

	@Transactional
	public AppointmentResponse start(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		return transition(
				organisationId, actorUserId, actor, requestId, appointmentId,
				request.commandRequestId(), request.version(),
				AppointmentStatus.IN_PROGRESS, null, null, null);
	}

	@Transactional
	public AppointmentResponse complete(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		return transition(
				organisationId, actorUserId, actor, requestId, appointmentId,
				request.commandRequestId(), request.version(),
				AppointmentStatus.COMPLETED, null, null, null);
	}

	@Transactional
	public AppointmentResponse markNoShow(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		return transition(
				organisationId, actorUserId, actor, requestId, appointmentId,
				request.commandRequestId(), request.version(),
				AppointmentStatus.NO_SHOW, null, null, null);
	}

	@Transactional
	public AppointmentResponse reschedule(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			SchedulingDoctorResource doctor,
			String requestId,
			UUID appointmentId,
			RescheduleAppointmentRequest request) {
		Appointment appointment = find(organisationId, appointmentId);
		if (!doctor.organisationId().equals(organisationId)
				|| !doctor.userId().equals(appointment.getDoctorUserId())
				|| !doctor.membershipId().equals(
						appointment.getDoctorMembershipId())) {
			throw new AppointmentDoctorNotFoundException();
		}
		DoctorAvailabilitySchedule schedule = availabilityRepository
				.findByOrganisationIdAndDoctorUserId(
						organisationId, doctor.userId())
				.orElseThrow(AvailabilityNotFoundException::new);
		if (!schedule.getDoctorMembershipId().equals(doctor.membershipId())) {
			throw new AppointmentDoctorNotFoundException();
		}
		ZoneId zone = ZoneId.of(schedule.getTimeZone());
		LocalDate localDate = request.startsAt().atZone(zone).toLocalDate();
		AvailableSlotResponse slot = slotCalculationService.calculate(
				schedule, localDate, localDate)
				.stream()
				.filter(candidate -> candidate.startsAt().equals(request.startsAt()))
				.findFirst()
				.orElseThrow(AppointmentSlotUnavailableException::new);
		boolean occupied = appointmentRepository.findBlockingDoctorAppointments(
				organisationId,
				doctor.userId(),
				AppointmentStatus.blockingStatuses(),
				slot.startsAt(),
				slot.endsAt())
				.stream()
				.anyMatch(candidate -> !candidate.getId().equals(appointmentId));
		if (occupied) {
			throw new AppointmentSlotUnavailableException();
		}
		return transition(
				organisationId, actorUserId, actor, requestId, appointmentId,
				request.commandRequestId(), request.version(),
				AppointmentStatus.RESCHEDULED, request.reason(), slot, schedule);
	}

	private AppointmentResponse transition(
			UUID organisationId,
			UUID actorUserId,
			OrganisationContextResource actor,
			String requestId,
			UUID appointmentId,
			UUID commandRequestId,
			long version,
			AppointmentStatus target,
			String reason,
			AvailableSlotResponse slot,
			DoctorAvailabilitySchedule schedule) {
		Appointment appointment = find(organisationId, appointmentId);
		policy.requireActorCanTransition(appointment, actorUserId, actor, target);
		AppointmentAuditEventType eventType = eventType(target);
		Instant requestedStart = slot == null ? null : slot.startsAt();
		AppointmentAuditEvent existing = auditRepository
				.findByOrganisationIdAndCommandRequestId(
						organisationId, commandRequestId)
				.orElse(null);
		if (existing != null) {
			if (!existing.matchesCommand(
					appointmentId, actorUserId, eventType, reason, requestedStart)) {
				throw new AppointmentCommandConflictException();
			}
			return mapper.response(appointment);
		}
		if (appointment.getVersion() != version) {
			throw new ConcurrentAppointmentModificationException();
		}
		policy.requireTransition(appointment.getStatus(), target);
		policy.requireTemporalTransition(appointment, target, clock.instant());
		AppointmentStatus previousStatus = appointment.getStatus();
		Instant previousStartsAt = appointment.getStartsAt();
		switch (target) {
			case CONFIRMED -> appointment.confirm(clock);
			case REJECTED -> appointment.reject(reason, clock);
			case CANCELLED -> appointment.cancel(reason, clock);
			case RESCHEDULED -> appointment.reschedule(
					schedule.getId(), slot.startsAt(), slot.endsAt(),
					schedule.getTimeZone(), schedule.getLocationLabel(),
					reason, clock);
			case CHECKED_IN -> appointment.checkIn(clock);
			case IN_PROGRESS -> appointment.start(clock);
			case COMPLETED -> appointment.complete(clock);
			case NO_SHOW -> appointment.markNoShow(clock);
			default -> throw new InvalidAppointmentTransitionException();
		}
		try {
			appointmentRepository.saveAndFlush(appointment);
			AppointmentAuditEvent auditEvent = auditRepository.saveAndFlush(
					AppointmentAuditEvent.transitioned(
					appointment,
					previousStatus,
					previousStartsAt,
					eventType,
					commandRequestId,
					reason,
					actorUserId,
					actor.membershipId(),
					requestId));
			outboxRecorder.record(appointment, auditEvent);
			return mapper.response(appointment);
		}
		catch (ObjectOptimisticLockingFailureException conflict) {
			throw new ConcurrentAppointmentModificationException();
		}
		catch (DataIntegrityViolationException conflict) {
			if (target == AppointmentStatus.RESCHEDULED) {
				throw new AppointmentSlotUnavailableException();
			}
			throw new AppointmentCommandConflictException();
		}
	}

	private Appointment find(UUID organisationId, UUID appointmentId) {
		return appointmentRepository
				.findByIdAndOrganisationId(appointmentId, organisationId)
				.orElseThrow(AppointmentNotFoundException::new);
	}

	private static AppointmentAuditEventType eventType(AppointmentStatus target) {
		return switch (target) {
			case CONFIRMED -> AppointmentAuditEventType.APPOINTMENT_CONFIRMED;
			case REJECTED -> AppointmentAuditEventType.APPOINTMENT_REJECTED;
			case RESCHEDULED -> AppointmentAuditEventType.APPOINTMENT_RESCHEDULED;
			case CANCELLED -> AppointmentAuditEventType.APPOINTMENT_CANCELLED;
			case CHECKED_IN -> AppointmentAuditEventType.APPOINTMENT_CHECKED_IN;
			case IN_PROGRESS -> AppointmentAuditEventType.APPOINTMENT_STARTED;
			case COMPLETED -> AppointmentAuditEventType.APPOINTMENT_COMPLETED;
			case NO_SHOW -> AppointmentAuditEventType.APPOINTMENT_NO_SHOW;
			default -> throw new InvalidAppointmentTransitionException();
		};
	}
}
