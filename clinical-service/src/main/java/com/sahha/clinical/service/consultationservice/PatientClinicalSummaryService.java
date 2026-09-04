package com.sahha.clinical.service.consultationservice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.clinical.client.scheduling.ClinicalPatientAccessContextResource;
import com.sahha.clinical.client.scheduling.SchedulingClinicalContextClient;
import com.sahha.clinical.dto.response.ClinicalRecordResponse;
import com.sahha.clinical.dto.response.PatientClinicalSummaryResponse;
import com.sahha.clinical.entity.ClinicalHistoryCategory;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.entity.ConsultationStatus;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.exception.ClinicalAppointmentNotFoundException;
import com.sahha.clinical.mapper.ClinicalRecordAssembler;
import com.sahha.clinical.repository.ConsultationRepository;
import com.sahha.clinical.service.clinicalauditservice.ClinicalAccessAuditService;

@Service
public class PatientClinicalSummaryService {

	private static final int MAXIMUM_ENCOUNTERS = 20;

	private final SchedulingClinicalContextClient schedulingClient;
	private final ConsultationRepository consultationRepository;
	private final ClinicalRecordAssembler assembler;
	private final ClinicalAccessAuditService accessAuditService;
	private final Clock clock;

	public PatientClinicalSummaryService(
			SchedulingClinicalContextClient schedulingClient,
			ConsultationRepository consultationRepository,
			ClinicalRecordAssembler assembler,
			ClinicalAccessAuditService accessAuditService,
			Clock clock) {
		this.schedulingClient = schedulingClient;
		this.consultationRepository = consultationRepository;
		this.assembler = assembler;
		this.accessAuditService = accessAuditService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PatientClinicalSummaryResponse find(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			UUID patientRegistrationId,
			String requestId) {
		ClinicalPatientAccessContextResource access;
		try {
			access = schedulingClient.resolvePatientAccess(
					patientRegistrationId, accessToken);
			requireMatchingAccess(
					access, organisationId, actorUserId, patientRegistrationId);
		}
		catch (ClinicalAccessDeniedException
				| ClinicalAppointmentNotFoundException denied) {
			accessAuditService.record(
					organisationId, actorUserId, "PATIENT_SUMMARY",
					patientRegistrationId, patientRegistrationId, null,
					"DENIED", "NO_ACTIVE_CARE_RELATIONSHIP", requestId);
			throw denied;
		}
		List<PatientClinicalSummaryResponse.EncounterSummary> encounters;
		try {
			encounters = consultationRepository
						.findByOrganisationIdAndPatientRegistrationIdAndStatusOrderByFinalizedAtDescIdDesc(
								organisationId,
								patientRegistrationId,
								ConsultationStatus.FINALIZED,
								PageRequest.of(0, MAXIMUM_ENCOUNTERS))
						.stream()
						.peek(consultation -> requirePatient(
								consultation, access.patientId()))
						.map(assembler::assemble)
						.map(PatientClinicalSummaryService::summary)
						.toList();
		}
		catch (ClinicalAccessDeniedException denied) {
			accessAuditService.record(
					organisationId, actorUserId, "PATIENT_SUMMARY",
					patientRegistrationId, patientRegistrationId,
					access.appointmentId(), "DENIED", "PATIENT_ID_MISMATCH",
					requestId);
			throw denied;
		}
		PatientClinicalSummaryResponse response = new PatientClinicalSummaryResponse(
				organisationId,
				patientRegistrationId,
				access.patientId(),
				new PatientClinicalSummaryResponse.CareRelationship(
						access.appointmentId(), access.status()),
				encounters,
				Instant.now(clock));
		accessAuditService.record(
				organisationId, actorUserId, "PATIENT_SUMMARY",
				patientRegistrationId, patientRegistrationId,
				access.appointmentId(), "GRANTED", "ACTIVE_APPOINTMENT", requestId);
		return response;
	}

	private static PatientClinicalSummaryResponse.EncounterSummary summary(
			ClinicalRecordResponse record) {
		return new PatientClinicalSummaryResponse.EncounterSummary(
				record.id(),
				record.appointmentId(),
				record.doctorUserId(),
				record.finalizedAt(),
				record.reasonForConsultation(),
				record.diagnoses().stream()
						.map(diagnosis ->
								new PatientClinicalSummaryResponse.DiagnosisSummary(
										diagnosis.code(), diagnosis.codeSystem(),
										diagnosis.label(), diagnosis.type(), diagnosis.status()))
						.toList(),
				record.medications().stream()
						.map(medication ->
								new PatientClinicalSummaryResponse.MedicationSummary(
										medication.kind(), medication.name(),
										medication.strength(), medication.dosage(),
										medication.frequency(), medication.route(),
										medication.duration()))
						.toList(),
				record.history().stream()
						.filter(item -> item.category()
								== ClinicalHistoryCategory.ALLERGY)
						.map(ClinicalRecordResponse.HistoryItem::description)
						.toList());
	}

	private static void requireMatchingAccess(
			ClinicalPatientAccessContextResource access,
			UUID organisationId,
			UUID actorUserId,
			UUID patientRegistrationId) {
		if (!organisationId.equals(access.organisationId())
				|| !actorUserId.equals(access.doctorUserId())
				|| !patientRegistrationId.equals(access.patientRegistrationId())) {
			throw new ClinicalAccessDeniedException();
		}
	}

	private static void requirePatient(
			Consultation consultation, UUID expectedPatientId) {
		if (!expectedPatientId.equals(consultation.getPatientId())) {
			throw new ClinicalAccessDeniedException();
		}
	}
}
