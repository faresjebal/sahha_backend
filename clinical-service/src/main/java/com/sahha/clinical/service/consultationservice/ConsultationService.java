package com.sahha.clinical.service.consultationservice;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.clinical.client.scheduling.ClinicalAppointmentContextResource;
import com.sahha.clinical.client.scheduling.SchedulingClinicalContextClient;
import com.sahha.clinical.dto.request.UpdateConsultationDraftRequest;
import com.sahha.clinical.dto.response.ConsultationResponse;
import com.sahha.clinical.entity.ClinicalAuditEventType;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.entity.ConsultationStatus;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.exception.ConcurrentConsultationModificationException;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.exception.ConsultationStateConflictException;
import com.sahha.clinical.mapper.ConsultationMapper;
import com.sahha.clinical.repository.ConsultationRepository;
import com.sahha.clinical.service.clinicalauditservice.ClinicalAuditRecorder;

@Service
public class ConsultationService {

	private static final String REQUIRED_APPOINTMENT_STATUS = "IN_PROGRESS";
	private final ConsultationRepository consultationRepository;
	private final SchedulingClinicalContextClient schedulingClient;
	private final ClinicalAuditRecorder auditRecorder;
	private final ConsultationMapper mapper;
	private final Clock clock;

	public ConsultationService(
			ConsultationRepository consultationRepository,
			SchedulingClinicalContextClient schedulingClient,
			ClinicalAuditRecorder auditRecorder,
			ConsultationMapper mapper,
			Clock clock) {
		this.consultationRepository = consultationRepository;
		this.schedulingClient = schedulingClient;
		this.auditRecorder = auditRecorder;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional
	public ConsultationCreationResult create(
			UUID activeOrganisationId,
			UUID actorUserId,
			String accessToken,
			UUID appointmentId,
			String requestId) {
		ClinicalAppointmentContextResource context =
				schedulingClient.resolve(appointmentId, accessToken);
		requireMatchingDoctorContext(activeOrganisationId, actorUserId, context);
		if (!REQUIRED_APPOINTMENT_STATUS.equals(context.status())) {
			throw new ConsultationStateConflictException();
		}

		Consultation candidate = Consultation.draft(
				context.organisationId(), context.appointmentId(),
				context.patientRegistrationId(), context.patientId(),
				context.doctorUserId(), context.doctorMembershipId(), clock);
		int inserted = consultationRepository.insertDraftIfAbsent(
				candidate.getId(), candidate.getOrganisationId(),
				candidate.getAppointmentId(), candidate.getPatientRegistrationId(),
				candidate.getPatientId(), candidate.getDoctorUserId(),
				candidate.getDoctorMembershipId(), candidate.getCreatedAt());
		Consultation consultation = consultationRepository
				.findByOrganisationIdAndAppointmentId(
						activeOrganisationId, appointmentId)
				.orElseThrow(ConsultationNotFoundException::new);
		requireSameContext(consultation, context);
		if (inserted == 1) {
			auditRecorder.recordCommand(
					consultation, actorUserId,
					ClinicalAuditEventType.CONSULTATION_DRAFT_CREATED, requestId);
		}
		return new ConsultationCreationResult(
				mapper.toResponse(consultation), inserted == 1);
	}

	@Transactional(readOnly = true)
	public ConsultationResponse find(
			UUID activeOrganisationId,
			UUID actorUserId,
			UUID consultationId) {
		Consultation consultation = consultationRepository
				.findByIdAndOrganisationIdAndDoctorUserId(
						consultationId, activeOrganisationId, actorUserId)
				.orElseThrow(ConsultationNotFoundException::new);
		return mapper.toResponse(consultation);
	}

	@Transactional
	public ConsultationResponse updateDraft(
			UUID activeOrganisationId,
			UUID actorUserId,
			UUID consultationId,
			UpdateConsultationDraftRequest request,
			String requestId) {
		Consultation consultation = consultationRepository
				.findOwnedDraftForUpdate(
						consultationId, activeOrganisationId, actorUserId)
				.orElseThrow(ConsultationNotFoundException::new);
		if (consultation.getVersion() != request.version()) {
			throw new ConcurrentConsultationModificationException();
		}
		if (consultation.getStatus() != ConsultationStatus.DRAFT) {
			throw new ConsultationStateConflictException();
		}
		consultation.updateDraft(
				request.reasonForConsultation(), request.draftNotes(), clock);
		Consultation updated = consultationRepository.saveAndFlush(consultation);
		auditRecorder.recordCommand(
				updated, actorUserId,
				ClinicalAuditEventType.CONSULTATION_DRAFT_UPDATED, requestId);
		return mapper.toResponse(updated);
	}

	private static void requireMatchingDoctorContext(
			UUID activeOrganisationId,
			UUID actorUserId,
			ClinicalAppointmentContextResource context) {
		if (!activeOrganisationId.equals(context.organisationId())
				|| !actorUserId.equals(context.doctorUserId())) {
			throw new ClinicalAccessDeniedException();
		}
	}

	private static void requireSameContext(
			Consultation consultation,
			ClinicalAppointmentContextResource context) {
		if (!consultation.getPatientId().equals(context.patientId())
				|| !consultation.getPatientRegistrationId().equals(
						context.patientRegistrationId())
				|| !consultation.getDoctorUserId().equals(context.doctorUserId())
				|| !consultation.getDoctorMembershipId().equals(
						context.doctorMembershipId())) {
			throw new ClinicalAccessDeniedException();
		}
	}
}
