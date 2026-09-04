package com.sahha.clinical.service.consultationservice;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.clinical.client.scheduling.ClinicalCompletionRecoveryCommand;
import com.sahha.clinical.client.scheduling.SchedulingClinicalContextClient;
import com.sahha.clinical.dto.request.RecoverAppointmentCompletionRequest;
import com.sahha.clinical.dto.response.AppointmentCompletionRecoveryResponse;
import com.sahha.clinical.entity.ClinicalOutboxEvent;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.entity.ConsultationStatus;
import com.sahha.clinical.exception.AppointmentCompletionConflictException;
import com.sahha.clinical.exception.ConcurrentConsultationModificationException;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.exception.ConsultationStateConflictException;
import com.sahha.clinical.repository.ClinicalOutboxEventRepository;
import com.sahha.clinical.repository.ConsultationRepository;

@Service
public class AppointmentCompletionRecoveryService {

	private static final String FINALIZED_EVENT = "consultation.finalised.v1";
	private final ConsultationRepository consultationRepository;
	private final ClinicalOutboxEventRepository outboxRepository;
	private final SchedulingClinicalContextClient schedulingClient;

	public AppointmentCompletionRecoveryService(
			ConsultationRepository consultationRepository,
			ClinicalOutboxEventRepository outboxRepository,
			SchedulingClinicalContextClient schedulingClient) {
		this.consultationRepository = consultationRepository;
		this.outboxRepository = outboxRepository;
		this.schedulingClient = schedulingClient;
	}

	public AppointmentCompletionRecoveryResponse recover(
			UUID organisationId,
			UUID actorUserId,
			UUID consultationId,
			RecoverAppointmentCompletionRequest request,
			String accessToken,
			String csrfToken) {
		Consultation consultation = consultationRepository
				.findByIdAndOrganisationIdAndDoctorUserId(
						consultationId, organisationId, actorUserId)
				.orElseThrow(ConsultationNotFoundException::new);
		if (consultation.getVersion() != request.version()) {
			throw new ConcurrentConsultationModificationException();
		}
		if (consultation.getStatus() != ConsultationStatus.FINALIZED) {
			throw new ConsultationStateConflictException();
		}
		ClinicalOutboxEvent finalization = outboxRepository
				.findFirstByAggregateIdAndEventTypeOrderByOccurredAtDesc(
						consultationId, FINALIZED_EVENT)
				.orElseThrow(ConsultationStateConflictException::new);
		long resourceVersion = resourceVersion(finalization);
		var result = schedulingClient.recoverCompletion(
				consultation.getAppointmentId(),
				new ClinicalCompletionRecoveryCommand(
						finalization.getId(), consultationId, resourceVersion,
						finalization.getOccurredAt()),
				accessToken,
				csrfToken);
		if ("CONFLICT".equals(result.outcome())) {
			throw new AppointmentCompletionConflictException(result.conflictCode());
		}
		return new AppointmentCompletionRecoveryResponse(
				consultationId,
				consultation.getAppointmentId(),
				result.outcome(),
				result.duplicate(),
				result.appointmentStatus(),
				result.appointmentVersion());
	}

	private static long resourceVersion(ClinicalOutboxEvent event) {
		Object value = event.getPayload().get("resourceVersion");
		if (value instanceof Number number && number.longValue() >= 0) {
			return number.longValue();
		}
		throw new ConsultationStateConflictException();
	}
}
