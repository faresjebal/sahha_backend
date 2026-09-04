package com.sahha.clinical.service.consultationservice;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.clinical.dto.response.ClinicalAttachmentContextResponse;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.repository.ConsultationRepository;

@Service
public class ConsultationAttachmentAccessService {

	private final ConsultationRepository consultationRepository;

	public ConsultationAttachmentAccessService(
			ConsultationRepository consultationRepository) {
		this.consultationRepository = consultationRepository;
	}

	@Transactional(readOnly = true)
	public ClinicalAttachmentContextResponse resolveUploadContext(
			UUID organisationId,
			UUID actorUserId,
			UUID consultationId) {
		Consultation consultation = consultationRepository
				.findByIdAndOrganisationIdAndDoctorUserId(
						consultationId, organisationId, actorUserId)
				.orElseThrow(ConsultationNotFoundException::new);
		return new ClinicalAttachmentContextResponse(
				consultation.getId(), consultation.getOrganisationId(),
				consultation.getPatientRegistrationId(), consultation.getPatientId(),
				consultation.getDoctorUserId(), consultation.getStatus().name(),
				consultation.getVersion());
	}
}
