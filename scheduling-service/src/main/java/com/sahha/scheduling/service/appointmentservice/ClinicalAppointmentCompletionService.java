package com.sahha.scheduling.service.appointmentservice;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.scheduling.dto.response.ClinicalAppointmentCompletionResponse;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentAuditEvent;
import com.sahha.scheduling.entity.AppointmentAuditEventType;
import com.sahha.scheduling.entity.AppointmentStatus;
import com.sahha.scheduling.entity.ClinicalCompletionOutcome;
import com.sahha.scheduling.entity.ConsumedClinicalEvent;
import com.sahha.scheduling.event.ClinicalConsultationEventV1;
import com.sahha.scheduling.event.ClinicalEventSource;
import com.sahha.scheduling.outbox.AppointmentOutboxRecorder;
import com.sahha.scheduling.repository.AppointmentAuditEventRepository;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.repository.ConsumedClinicalEventRepository;

@Service
public class ClinicalAppointmentCompletionService {

	private final AppointmentRepository appointmentRepository;
	private final ConsumedClinicalEventRepository consumedRepository;
	private final AppointmentAuditEventRepository auditRepository;
	private final AppointmentOutboxRecorder outboxRecorder;
	private final Clock clock;

	public ClinicalAppointmentCompletionService(
			AppointmentRepository appointmentRepository,
			ConsumedClinicalEventRepository consumedRepository,
			AppointmentAuditEventRepository auditRepository,
			AppointmentOutboxRecorder outboxRecorder,
			Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.consumedRepository = consumedRepository;
		this.auditRepository = auditRepository;
		this.outboxRecorder = outboxRecorder;
		this.clock = clock;
	}

	@Transactional
	public ClinicalAppointmentCompletionResponse process(
			ClinicalConsultationEventV1 event,
			ClinicalEventSource source,
			UUID requiredDoctorMembershipId) {
		ConsumedClinicalEvent existing = consumedRepository
				.findById(event.eventId()).orElse(null);
		if (existing != null) {
			return response(existing, true);
		}
		Appointment appointment = appointmentRepository
				.findForClinicalCompletion(
						event.appointmentId(), event.organisationId())
				.orElse(null);
		if (appointment == null) {
			return conflict(
					event, source, null,
					"APPOINTMENT_NOT_FOUND_OR_ORGANISATION_MISMATCH");
		}
		existing = consumedRepository.findById(event.eventId()).orElse(null);
		if (existing != null) {
			return response(existing, true);
		}
		if (!event.actorUserId().equals(appointment.getDoctorUserId())) {
			return conflict(event, source, appointment,
					"ACTOR_NOT_APPOINTMENT_DOCTOR");
		}
		if (requiredDoctorMembershipId != null
				&& !requiredDoctorMembershipId.equals(
						appointment.getDoctorMembershipId())) {
			return conflict(event, source, appointment,
					"DOCTOR_MEMBERSHIP_MISMATCH");
		}
		if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
			ConsumedClinicalEvent consumed = save(
					event, source, ClinicalCompletionOutcome.IDEMPOTENT,
					AppointmentStatus.COMPLETED, AppointmentStatus.COMPLETED,
					appointment.getVersion(), null);
			return response(consumed, false);
		}
		if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
			return conflict(event, source, appointment,
					"APPOINTMENT_NOT_IN_PROGRESS");
		}

		AppointmentStatus previousStatus = appointment.getStatus();
		appointment.complete(clock);
		Appointment completed = appointmentRepository.saveAndFlush(appointment);
		AppointmentAuditEvent audit = auditRepository.saveAndFlush(
				AppointmentAuditEvent.transitioned(
						completed,
						previousStatus,
						completed.getStartsAt(),
						AppointmentAuditEventType.APPOINTMENT_COMPLETED,
						event.eventId(),
						null,
						event.actorUserId(),
						completed.getDoctorMembershipId(),
						"clinical-finalisation:" + event.eventId()));
		outboxRecorder.record(completed, audit);
		ConsumedClinicalEvent consumed = save(
				event, source, ClinicalCompletionOutcome.APPLIED,
				previousStatus, completed.getStatus(), completed.getVersion(), null);
		return response(consumed, false);
	}

	private ClinicalAppointmentCompletionResponse conflict(
			ClinicalConsultationEventV1 event,
			ClinicalEventSource source,
			Appointment appointment,
			String conflictCode) {
		AppointmentStatus status = appointment == null
				? null : appointment.getStatus();
		Long version = appointment == null ? null : appointment.getVersion();
		return response(save(
				event, source, ClinicalCompletionOutcome.CONFLICT,
				status, status, version, conflictCode), false);
	}

	private ConsumedClinicalEvent save(
			ClinicalConsultationEventV1 event,
			ClinicalEventSource source,
			ClinicalCompletionOutcome outcome,
			AppointmentStatus previousStatus,
			AppointmentStatus resultingStatus,
			Long appointmentVersion,
			String conflictCode) {
		Instant now = clock.instant();
		Instant processedAt = now.isBefore(event.occurredAt())
				? event.occurredAt() : now;
		return consumedRepository.saveAndFlush(ConsumedClinicalEvent.record(
				event, source, outcome, previousStatus, resultingStatus,
				appointmentVersion, conflictCode, processedAt));
	}

	private static ClinicalAppointmentCompletionResponse response(
			ConsumedClinicalEvent event, boolean duplicate) {
		return new ClinicalAppointmentCompletionResponse(
				event.getEventId(), event.getAppointmentId(), event.getOutcome(),
				duplicate, event.getResultingStatus(), event.getAppointmentVersion(),
				event.getConflictCode());
	}
}
