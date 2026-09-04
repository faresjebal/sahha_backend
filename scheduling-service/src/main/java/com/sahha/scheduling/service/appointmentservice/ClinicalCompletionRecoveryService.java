package com.sahha.scheduling.service.appointmentservice;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.scheduling.dto.request.ClinicalCompletionRecoveryRequest;
import com.sahha.scheduling.dto.response.ClinicalAppointmentCompletionResponse;
import com.sahha.scheduling.dto.response.ClinicalAppointmentContextResponse;
import com.sahha.scheduling.event.ClinicalConsultationEventV1;
import com.sahha.scheduling.event.ClinicalEventDecoder;
import com.sahha.scheduling.event.ClinicalEventSource;

@Service
public class ClinicalCompletionRecoveryService {

	private final ClinicalAppointmentContextService contextService;
	private final ClinicalAppointmentCompletionService completionService;

	public ClinicalCompletionRecoveryService(
			ClinicalAppointmentContextService contextService,
			ClinicalAppointmentCompletionService completionService) {
		this.contextService = contextService;
		this.completionService = completionService;
	}

	public ClinicalAppointmentCompletionResponse recover(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			UUID appointmentId,
			ClinicalCompletionRecoveryRequest request) {
		ClinicalAppointmentContextResponse context = contextService.resolve(
				organisationId, actorUserId, accessToken, appointmentId);
		ClinicalConsultationEventV1 event = new ClinicalConsultationEventV1(
				request.eventId(),
				ClinicalEventDecoder.FINALIZED,
				1,
				request.occurredAt(),
				actorUserId,
				organisationId,
				appointmentId,
				request.consultationId(),
				"FINALIZED",
				request.clinicalResourceVersion());
		return completionService.process(
				event, ClinicalEventSource.rest(), context.doctorMembershipId());
	}
}
