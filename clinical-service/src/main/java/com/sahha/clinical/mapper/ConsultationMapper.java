package com.sahha.clinical.mapper;

import org.springframework.stereotype.Component;

import com.sahha.clinical.dto.response.ConsultationResponse;
import com.sahha.clinical.entity.Consultation;

@Component
public class ConsultationMapper {

	public ConsultationResponse toResponse(Consultation consultation) {
		return new ConsultationResponse(
				consultation.getId(),
				consultation.getOrganisationId(),
				consultation.getAppointmentId(),
				consultation.getPatientRegistrationId(),
				consultation.getPatientId(),
				consultation.getDoctorUserId(),
				consultation.getDoctorMembershipId(),
				consultation.getStatus(),
				consultation.getReasonForConsultation(),
				consultation.getDraftNotes(),
				consultation.getCreatedAt(),
				consultation.getUpdatedAt(),
				consultation.getVersion());
	}
}
