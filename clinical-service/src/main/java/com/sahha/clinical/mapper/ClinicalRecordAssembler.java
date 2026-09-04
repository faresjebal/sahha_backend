package com.sahha.clinical.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sahha.clinical.dto.response.ClinicalRecordResponse;
import com.sahha.clinical.entity.ClinicalCorrection;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.entity.CorrectionTargetType;
import com.sahha.clinical.entity.DiagnosisStatus;
import com.sahha.clinical.entity.DiagnosisType;
import com.sahha.clinical.entity.MedicationKind;
import com.sahha.clinical.entity.SymptomSeverity;
import com.sahha.clinical.repository.ClinicalCorrectionRepository;
import com.sahha.clinical.repository.ClinicalDiagnosisRepository;
import com.sahha.clinical.repository.ClinicalExaminationFindingRepository;
import com.sahha.clinical.repository.ClinicalHistoryEntryRepository;
import com.sahha.clinical.repository.ClinicalMedicationItemRepository;
import com.sahha.clinical.repository.ClinicalSymptomRepository;
import com.sahha.clinical.repository.ClinicalVitalSignsRepository;

@Component
public class ClinicalRecordAssembler {

	private final ClinicalSymptomRepository symptomRepository;
	private final ClinicalHistoryEntryRepository historyRepository;
	private final ClinicalVitalSignsRepository vitalSignsRepository;
	private final ClinicalExaminationFindingRepository examinationRepository;
	private final ClinicalDiagnosisRepository diagnosisRepository;
	private final ClinicalMedicationItemRepository medicationRepository;
	private final ClinicalCorrectionRepository correctionRepository;

	public ClinicalRecordAssembler(
			ClinicalSymptomRepository symptomRepository,
			ClinicalHistoryEntryRepository historyRepository,
			ClinicalVitalSignsRepository vitalSignsRepository,
			ClinicalExaminationFindingRepository examinationRepository,
			ClinicalDiagnosisRepository diagnosisRepository,
			ClinicalMedicationItemRepository medicationRepository,
			ClinicalCorrectionRepository correctionRepository) {
		this.symptomRepository = symptomRepository;
		this.historyRepository = historyRepository;
		this.vitalSignsRepository = vitalSignsRepository;
		this.examinationRepository = examinationRepository;
		this.diagnosisRepository = diagnosisRepository;
		this.medicationRepository = medicationRepository;
		this.correctionRepository = correctionRepository;
	}

	public ClinicalRecordResponse assemble(Consultation consultation) {
		List<ClinicalCorrection> corrections = correctionRepository
				.findByConsultationIdOrderByConsultationVersionAscCorrectedAtAscIdAsc(
						consultation.getId());
		CorrectionOverlay overlay = new CorrectionOverlay(corrections);
		var symptoms = symptomRepository
				.findByConsultationIdOrderByPosition(consultation.getId()).stream()
				.map(item -> new ClinicalRecordResponse.SymptomItem(
						item.getId(),
						overlay.text(CorrectionTargetType.SYMPTOM, item.getId(),
								"name", item.getName()),
						overlay.text(CorrectionTargetType.SYMPTOM, item.getId(),
								"onsetDescription", item.getOnsetDescription()),
						overlay.enumValue(CorrectionTargetType.SYMPTOM, item.getId(),
								"severity", item.getSeverity(), SymptomSeverity.class),
						overlay.text(CorrectionTargetType.SYMPTOM, item.getId(),
								"notes", item.getNotes())))
				.toList();
		var history = historyRepository
				.findByConsultationIdOrderByPosition(consultation.getId()).stream()
				.map(item -> new ClinicalRecordResponse.HistoryItem(
						item.getId(),
						overlay.enumValue(CorrectionTargetType.HISTORY, item.getId(),
								"category", item.getCategory(),
								com.sahha.clinical.entity.ClinicalHistoryCategory.class),
						overlay.text(CorrectionTargetType.HISTORY, item.getId(),
								"description", item.getDescription()),
						overlay.text(CorrectionTargetType.HISTORY, item.getId(),
								"notes", item.getNotes())))
				.toList();
		var vitals = vitalSignsRepository.findByConsultationId(consultation.getId())
				.map(item -> new ClinicalRecordResponse.VitalSignsItem(
						item.getId(),
						overlay.instant(CorrectionTargetType.VITAL_SIGNS, item.getId(),
								"measuredAt", item.getMeasuredAt()),
						overlay.decimal(CorrectionTargetType.VITAL_SIGNS, item.getId(),
								"temperatureCelsius", item.getTemperatureCelsius()),
						overlay.integer(CorrectionTargetType.VITAL_SIGNS, item.getId(),
								"systolicBloodPressure", item.getSystolicBloodPressure()),
						overlay.integer(CorrectionTargetType.VITAL_SIGNS, item.getId(),
								"diastolicBloodPressure", item.getDiastolicBloodPressure()),
						overlay.integer(CorrectionTargetType.VITAL_SIGNS, item.getId(),
								"heartRateBpm", item.getHeartRateBpm()),
						overlay.integer(CorrectionTargetType.VITAL_SIGNS, item.getId(),
								"respiratoryRateBpm", item.getRespiratoryRateBpm()),
						overlay.decimal(CorrectionTargetType.VITAL_SIGNS, item.getId(),
								"oxygenSaturationPercent", item.getOxygenSaturationPercent()),
						overlay.decimal(CorrectionTargetType.VITAL_SIGNS, item.getId(),
								"weightKg", item.getWeightKg()),
						overlay.decimal(CorrectionTargetType.VITAL_SIGNS, item.getId(),
								"heightCm", item.getHeightCm()),
						item.getSpecialtyMeasurements()))
				.orElse(null);
		var examinations = examinationRepository
				.findByConsultationIdOrderByPosition(consultation.getId()).stream()
				.map(item -> new ClinicalRecordResponse.ExaminationItem(
						item.getId(),
						overlay.text(CorrectionTargetType.EXAMINATION, item.getId(),
								"bodySystem", item.getBodySystem()),
						overlay.text(CorrectionTargetType.EXAMINATION, item.getId(),
								"finding", item.getFinding()),
						overlay.text(CorrectionTargetType.EXAMINATION, item.getId(),
								"notes", item.getNotes())))
				.toList();
		var diagnoses = diagnosisRepository
				.findByConsultationIdOrderByPosition(consultation.getId()).stream()
				.map(item -> new ClinicalRecordResponse.DiagnosisItem(
						item.getId(), item.getCode(), item.getCodeSystem(),
						overlay.text(CorrectionTargetType.DIAGNOSIS, item.getId(),
								"label", item.getLabel()),
						overlay.enumValue(CorrectionTargetType.DIAGNOSIS, item.getId(),
								"type", item.getType(), DiagnosisType.class),
						overlay.enumValue(CorrectionTargetType.DIAGNOSIS, item.getId(),
								"status", item.getStatus(), DiagnosisStatus.class),
						overlay.text(CorrectionTargetType.DIAGNOSIS, item.getId(),
								"notes", item.getNotes())))
				.toList();
		var medications = medicationRepository
				.findByConsultationIdOrderByPosition(consultation.getId()).stream()
				.map(item -> new ClinicalRecordResponse.MedicationItem(
						item.getId(),
						overlay.enumValue(CorrectionTargetType.MEDICATION, item.getId(),
								"kind", item.getKind(), MedicationKind.class),
						overlay.text(CorrectionTargetType.MEDICATION, item.getId(),
								"name", item.getName()),
						overlay.text(CorrectionTargetType.MEDICATION, item.getId(),
								"strength", item.getStrength()),
						overlay.text(CorrectionTargetType.MEDICATION, item.getId(),
								"form", item.getForm()),
						overlay.text(CorrectionTargetType.MEDICATION, item.getId(),
								"dosage", item.getDosage()),
						overlay.text(CorrectionTargetType.MEDICATION, item.getId(),
								"frequency", item.getFrequency()),
						overlay.text(CorrectionTargetType.MEDICATION, item.getId(),
								"route", item.getRoute()),
						overlay.text(CorrectionTargetType.MEDICATION, item.getId(),
								"duration", item.getDuration()),
						overlay.text(CorrectionTargetType.MEDICATION, item.getId(),
								"quantity", item.getQuantity()),
						overlay.text(CorrectionTargetType.MEDICATION, item.getId(),
								"specialInstructions", item.getSpecialInstructions())))
				.toList();
		var correctionItems = corrections.stream()
				.map(item -> new ClinicalRecordResponse.CorrectionItem(
						item.getId(), item.getTargetType(), item.getTargetId(),
						item.getFieldName(), item.getOldValue(), item.getNewValue(),
						item.getReason(), item.getActorUserId(),
						item.getConsultationVersion(), item.getCorrectedAt()))
				.toList();
		return new ClinicalRecordResponse(
				consultation.getId(), consultation.getOrganisationId(),
				consultation.getAppointmentId(), consultation.getPatientRegistrationId(),
				consultation.getPatientId(), consultation.getDoctorUserId(),
				consultation.getDoctorMembershipId(), consultation.getStatus(),
				overlay.text(CorrectionTargetType.CONSULTATION, null,
						"reasonForConsultation", consultation.getReasonForConsultation()),
				overlay.text(CorrectionTargetType.CONSULTATION, null,
						"clinicalAssessment", consultation.getClinicalAssessment()),
				overlay.text(CorrectionTargetType.CONSULTATION, null,
						"treatmentPlan", consultation.getTreatmentPlan()),
				overlay.text(CorrectionTargetType.CONSULTATION, null,
						"followUpInstructions", consultation.getFollowUpInstructions()),
				overlay.text(CorrectionTargetType.CONSULTATION, null,
						"additionalNotes", consultation.getDraftNotes()),
				symptoms, history, vitals, examinations, diagnoses, medications,
				correctionItems, consultation.getFinalizedAt(),
				consultation.getFinalizedByUserId(), consultation.getCreatedAt(),
				consultation.getUpdatedAt(), consultation.getVersion());
	}
}
