package com.sahha.clinical.service.consultationservice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sahha.clinical.entity.ClinicalCorrection;
import com.sahha.clinical.entity.ClinicalDiagnosis;
import com.sahha.clinical.entity.ClinicalExaminationFinding;
import com.sahha.clinical.entity.ClinicalHistoryCategory;
import com.sahha.clinical.entity.ClinicalHistoryEntry;
import com.sahha.clinical.entity.ClinicalMedicationItem;
import com.sahha.clinical.entity.ClinicalSymptom;
import com.sahha.clinical.entity.ClinicalVitalSigns;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.entity.CorrectionTargetType;
import com.sahha.clinical.entity.DiagnosisStatus;
import com.sahha.clinical.entity.DiagnosisType;
import com.sahha.clinical.entity.MedicationKind;
import com.sahha.clinical.entity.SymptomSeverity;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.repository.ClinicalCorrectionRepository;
import com.sahha.clinical.repository.ClinicalDiagnosisRepository;
import com.sahha.clinical.repository.ClinicalExaminationFindingRepository;
import com.sahha.clinical.repository.ClinicalHistoryEntryRepository;
import com.sahha.clinical.repository.ClinicalMedicationItemRepository;
import com.sahha.clinical.repository.ClinicalSymptomRepository;
import com.sahha.clinical.repository.ClinicalVitalSignsRepository;

@Component
public class ClinicalCorrectionPolicy {

	private final ClinicalSymptomRepository symptomRepository;
	private final ClinicalHistoryEntryRepository historyRepository;
	private final ClinicalVitalSignsRepository vitalSignsRepository;
	private final ClinicalExaminationFindingRepository examinationRepository;
	private final ClinicalDiagnosisRepository diagnosisRepository;
	private final ClinicalMedicationItemRepository medicationRepository;
	private final ClinicalCorrectionRepository correctionRepository;

	public ClinicalCorrectionPolicy(
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

	public CorrectionDecision decide(
			Consultation consultation,
			CorrectionTargetType targetType,
			UUID targetId,
			String fieldName,
			String requestedNewValue) {
		String field = required(fieldName, 64);
		String source = sourceValue(consultation, targetType, targetId, field);
		List<ClinicalCorrection> previous = correctionRepository
				.findByConsultationIdOrderByConsultationVersionAscCorrectedAtAscIdAsc(
						consultation.getId());
		String effectiveOld = source;
		for (ClinicalCorrection correction : previous) {
			if (correction.getTargetType() == targetType
					&& Objects.equals(correction.getTargetId(), targetId)
					&& correction.getFieldName().equals(field)) {
				effectiveOld = correction.getNewValue();
			}
		}
		String normalizedNew = normalize(targetType, field, requestedNewValue);
		if (Objects.equals(effectiveOld, normalizedNew)) {
			throw new IllegalArgumentException("correction must change the value");
		}
		return new CorrectionDecision(field, effectiveOld, normalizedNew);
	}

	private String sourceValue(
			Consultation consultation,
			CorrectionTargetType targetType,
			UUID targetId,
			String field) {
		if (targetType == CorrectionTargetType.CONSULTATION) {
			if (targetId != null) throw new IllegalArgumentException("targetId is invalid");
			return switch (field) {
				case "reasonForConsultation" -> consultation.getReasonForConsultation();
				case "clinicalAssessment" -> consultation.getClinicalAssessment();
				case "treatmentPlan" -> consultation.getTreatmentPlan();
				case "followUpInstructions" -> consultation.getFollowUpInstructions();
				case "additionalNotes" -> consultation.getDraftNotes();
				default -> throw new IllegalArgumentException("fieldName is invalid");
			};
		}
		if (targetId == null) throw new IllegalArgumentException("targetId is required");
		return switch (targetType) {
			case SYMPTOM -> symptomSource(
					owned(symptomRepository.findById(targetId).orElseThrow(
							ConsultationNotFoundException::new), consultation.getId()), field);
			case HISTORY -> historySource(
					owned(historyRepository.findById(targetId).orElseThrow(
							ConsultationNotFoundException::new), consultation.getId()), field);
			case VITAL_SIGNS -> vitalSource(
					owned(vitalSignsRepository.findById(targetId).orElseThrow(
							ConsultationNotFoundException::new), consultation.getId()), field);
			case EXAMINATION -> examinationSource(
					owned(examinationRepository.findById(targetId).orElseThrow(
							ConsultationNotFoundException::new), consultation.getId()), field);
			case DIAGNOSIS -> diagnosisSource(
					owned(diagnosisRepository.findById(targetId).orElseThrow(
							ConsultationNotFoundException::new), consultation.getId()), field);
			case MEDICATION -> medicationSource(
					owned(medicationRepository.findById(targetId).orElseThrow(
							ConsultationNotFoundException::new), consultation.getId()), field);
			case CONSULTATION -> throw new IllegalStateException();
		};
	}

	private static ClinicalSymptom owned(ClinicalSymptom value, UUID consultationId) {
		if (!value.getConsultationId().equals(consultationId)) throw new ConsultationNotFoundException();
		return value;
	}
	private static ClinicalHistoryEntry owned(ClinicalHistoryEntry value, UUID consultationId) {
		if (!value.getConsultationId().equals(consultationId)) throw new ConsultationNotFoundException();
		return value;
	}
	private static ClinicalVitalSigns owned(ClinicalVitalSigns value, UUID consultationId) {
		if (!value.getConsultationId().equals(consultationId)) throw new ConsultationNotFoundException();
		return value;
	}
	private static ClinicalExaminationFinding owned(ClinicalExaminationFinding value, UUID consultationId) {
		if (!value.getConsultationId().equals(consultationId)) throw new ConsultationNotFoundException();
		return value;
	}
	private static ClinicalDiagnosis owned(ClinicalDiagnosis value, UUID consultationId) {
		if (!value.getConsultationId().equals(consultationId)) throw new ConsultationNotFoundException();
		return value;
	}
	private static ClinicalMedicationItem owned(ClinicalMedicationItem value, UUID consultationId) {
		if (!value.getConsultationId().equals(consultationId)) throw new ConsultationNotFoundException();
		return value;
	}

	private static String symptomSource(ClinicalSymptom value, String field) {
		return switch (field) {
			case "name" -> value.getName();
			case "onsetDescription" -> value.getOnsetDescription();
			case "severity" -> name(value.getSeverity());
			case "notes" -> value.getNotes();
			default -> throw new IllegalArgumentException("fieldName is invalid");
		};
	}

	private static String historySource(ClinicalHistoryEntry value, String field) {
		return switch (field) {
			case "category" -> value.getCategory().name();
			case "description" -> value.getDescription();
			case "notes" -> value.getNotes();
			default -> throw new IllegalArgumentException("fieldName is invalid");
		};
	}

	private static String vitalSource(ClinicalVitalSigns value, String field) {
		return switch (field) {
			case "measuredAt" -> value.getMeasuredAt().toString();
			case "temperatureCelsius" -> decimal(value.getTemperatureCelsius());
			case "systolicBloodPressure" -> text(value.getSystolicBloodPressure());
			case "diastolicBloodPressure" -> text(value.getDiastolicBloodPressure());
			case "heartRateBpm" -> text(value.getHeartRateBpm());
			case "respiratoryRateBpm" -> text(value.getRespiratoryRateBpm());
			case "oxygenSaturationPercent" -> decimal(value.getOxygenSaturationPercent());
			case "weightKg" -> decimal(value.getWeightKg());
			case "heightCm" -> decimal(value.getHeightCm());
			default -> throw new IllegalArgumentException("fieldName is invalid");
		};
	}

	private static String examinationSource(ClinicalExaminationFinding value, String field) {
		return switch (field) {
			case "bodySystem" -> value.getBodySystem();
			case "finding" -> value.getFinding();
			case "notes" -> value.getNotes();
			default -> throw new IllegalArgumentException("fieldName is invalid");
		};
	}

	private static String diagnosisSource(ClinicalDiagnosis value, String field) {
		return switch (field) {
			case "label" -> value.getLabel();
			case "type" -> value.getType().name();
			case "status" -> value.getStatus().name();
			case "notes" -> value.getNotes();
			default -> throw new IllegalArgumentException("fieldName is invalid");
		};
	}

	private static String medicationSource(ClinicalMedicationItem value, String field) {
		return switch (field) {
			case "kind" -> value.getKind().name();
			case "name" -> value.getName();
			case "strength" -> value.getStrength();
			case "form" -> value.getForm();
			case "dosage" -> value.getDosage();
			case "frequency" -> value.getFrequency();
			case "route" -> value.getRoute();
			case "duration" -> value.getDuration();
			case "quantity" -> value.getQuantity();
			case "specialInstructions" -> value.getSpecialInstructions();
			default -> throw new IllegalArgumentException("fieldName is invalid");
		};
	}

	private static String normalize(
			CorrectionTargetType type, String field, String value) {
		return switch (type) {
			case CONSULTATION -> switch (field) {
				case "reasonForConsultation" -> optional(value, 1000);
				case "clinicalAssessment", "treatmentPlan", "followUpInstructions",
						"additionalNotes" -> optional(value, 20_000);
				default -> throw new IllegalArgumentException("fieldName is invalid");
			};
			case SYMPTOM -> switch (field) {
				case "name" -> required(value, 200);
				case "onsetDescription" -> optional(value, 300);
				case "severity" -> enumValue(value, SymptomSeverity.class, false);
				case "notes" -> optional(value, 1000);
				default -> throw new IllegalArgumentException("fieldName is invalid");
			};
			case HISTORY -> switch (field) {
				case "category" -> enumValue(value, ClinicalHistoryCategory.class, true);
				case "description" -> required(value, 1000);
				case "notes" -> optional(value, 1000);
				default -> throw new IllegalArgumentException("fieldName is invalid");
			};
			case VITAL_SIGNS -> switch (field) {
				case "measuredAt" -> Instant.parse(required(value, 64)).toString();
				case "temperatureCelsius" -> decimal(value, "25", "50");
				case "systolicBloodPressure" -> integer(value, 40, 300);
				case "diastolicBloodPressure" -> integer(value, 20, 200);
				case "heartRateBpm" -> integer(value, 20, 300);
				case "respiratoryRateBpm" -> integer(value, 4, 100);
				case "oxygenSaturationPercent" -> decimal(value, "0", "100");
				case "weightKg" -> decimal(value, "0.10", "700");
				case "heightCm" -> decimal(value, "10", "300");
				default -> throw new IllegalArgumentException("fieldName is invalid");
			};
			case EXAMINATION -> switch (field) {
				case "bodySystem" -> required(value, 160);
				case "finding" -> required(value, 1000);
				case "notes" -> optional(value, 1000);
				default -> throw new IllegalArgumentException("fieldName is invalid");
			};
			case DIAGNOSIS -> switch (field) {
				case "label" -> required(value, 300);
				case "type" -> enumValue(value, DiagnosisType.class, true);
				case "status" -> enumValue(value, DiagnosisStatus.class, true);
				case "notes" -> optional(value, 1000);
				default -> throw new IllegalArgumentException("fieldName is invalid");
			};
			case MEDICATION -> switch (field) {
				case "kind" -> enumValue(value, MedicationKind.class, true);
				case "name" -> required(value, 300);
				case "strength", "form", "route", "quantity" -> optional(value, 100);
				case "dosage", "frequency", "duration" -> optional(value, 160);
				case "specialInstructions" -> optional(value, 1000);
				default -> throw new IllegalArgumentException("fieldName is invalid");
			};
		};
	}

	private static String required(String value, int maximumLength) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("value is required");
		String stripped = value.strip();
		if (stripped.length() > maximumLength) throw new IllegalArgumentException("value is invalid");
		return stripped;
	}

	private static String optional(String value, int maximumLength) {
		if (value == null || value.isBlank()) return null;
		String stripped = value.strip();
		if (stripped.length() > maximumLength) throw new IllegalArgumentException("value is invalid");
		return stripped;
	}

	private static <T extends Enum<T>> String enumValue(
			String value, Class<T> enumType, boolean required) {
		String normalized = required ? required(value, 64) : optional(value, 64);
		if (normalized == null) return null;
		return Enum.valueOf(enumType, normalized).name();
	}

	private static String integer(String value, int minimum, int maximum) {
		if (value == null || value.isBlank()) return null;
		int parsed = Integer.parseInt(value.strip());
		if (parsed < minimum || parsed > maximum) throw new IllegalArgumentException("value is invalid");
		return Integer.toString(parsed);
	}

	private static String decimal(String value, String minimum, String maximum) {
		if (value == null || value.isBlank()) return null;
		BigDecimal parsed = new BigDecimal(value.strip());
		if (parsed.compareTo(new BigDecimal(minimum)) < 0
				|| parsed.compareTo(new BigDecimal(maximum)) > 0) {
			throw new IllegalArgumentException("value is invalid");
		}
		return parsed.stripTrailingZeros().toPlainString();
	}

	private static String text(Object value) {
		return value == null ? null : value.toString();
	}

	private static String decimal(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}
}
