package com.sahha.clinical.service.consultationservice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.clinical.dto.request.CreateClinicalCorrectionRequest;
import com.sahha.clinical.dto.request.FinalizeConsultationRequest;
import com.sahha.clinical.dto.request.ReplaceConsultationDraftRequest;
import com.sahha.clinical.dto.response.ClinicalRecordResponse;
import com.sahha.clinical.entity.ClinicalAuditEventType;
import com.sahha.clinical.entity.ClinicalCorrection;
import com.sahha.clinical.entity.ClinicalDiagnosis;
import com.sahha.clinical.entity.ClinicalExaminationFinding;
import com.sahha.clinical.entity.ClinicalHistoryEntry;
import com.sahha.clinical.entity.ClinicalMedicationItem;
import com.sahha.clinical.entity.ClinicalSymptom;
import com.sahha.clinical.entity.ClinicalVitalSigns;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.entity.ConsultationStatus;
import com.sahha.clinical.entity.DiagnosisStatus;
import com.sahha.clinical.entity.MedicationKind;
import com.sahha.clinical.exception.ConcurrentConsultationModificationException;
import com.sahha.clinical.exception.ConsultationIncompleteException;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.exception.ConsultationStateConflictException;
import com.sahha.clinical.mapper.ClinicalRecordAssembler;
import com.sahha.clinical.repository.ClinicalCorrectionRepository;
import com.sahha.clinical.repository.ClinicalDiagnosisRepository;
import com.sahha.clinical.repository.ClinicalExaminationFindingRepository;
import com.sahha.clinical.repository.ClinicalHistoryEntryRepository;
import com.sahha.clinical.repository.ClinicalMedicationItemRepository;
import com.sahha.clinical.repository.ClinicalSymptomRepository;
import com.sahha.clinical.repository.ClinicalVitalSignsRepository;
import com.sahha.clinical.repository.ConsultationRepository;
import com.sahha.clinical.service.clinicalauditservice.ClinicalAuditRecorder;
import com.sahha.clinical.service.clinicalauditservice.ClinicalAccessAuditService;

@Service
public class ClinicalRecordService {

	private static final Pattern SPECIALTY_KEY =
			Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,63}$");
	private final ConsultationRepository consultationRepository;
	private final ClinicalSymptomRepository symptomRepository;
	private final ClinicalHistoryEntryRepository historyRepository;
	private final ClinicalVitalSignsRepository vitalSignsRepository;
	private final ClinicalExaminationFindingRepository examinationRepository;
	private final ClinicalDiagnosisRepository diagnosisRepository;
	private final ClinicalMedicationItemRepository medicationRepository;
	private final ClinicalCorrectionRepository correctionRepository;
	private final ClinicalCorrectionPolicy correctionPolicy;
	private final ClinicalRecordAssembler assembler;
	private final ClinicalAuditRecorder auditRecorder;
	private final ClinicalAccessAuditService accessAuditService;
	private final Clock clock;

	public ClinicalRecordService(
			ConsultationRepository consultationRepository,
			ClinicalSymptomRepository symptomRepository,
			ClinicalHistoryEntryRepository historyRepository,
			ClinicalVitalSignsRepository vitalSignsRepository,
			ClinicalExaminationFindingRepository examinationRepository,
			ClinicalDiagnosisRepository diagnosisRepository,
			ClinicalMedicationItemRepository medicationRepository,
			ClinicalCorrectionRepository correctionRepository,
			ClinicalCorrectionPolicy correctionPolicy,
			ClinicalRecordAssembler assembler,
			ClinicalAuditRecorder auditRecorder,
			ClinicalAccessAuditService accessAuditService,
			Clock clock) {
		this.consultationRepository = consultationRepository;
		this.symptomRepository = symptomRepository;
		this.historyRepository = historyRepository;
		this.vitalSignsRepository = vitalSignsRepository;
		this.examinationRepository = examinationRepository;
		this.diagnosisRepository = diagnosisRepository;
		this.medicationRepository = medicationRepository;
		this.correctionRepository = correctionRepository;
		this.correctionPolicy = correctionPolicy;
		this.assembler = assembler;
		this.auditRecorder = auditRecorder;
		this.accessAuditService = accessAuditService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ClinicalRecordResponse find(
			UUID organisationId,
			UUID actorUserId,
			UUID consultationId,
			String requestId) {
		Consultation consultation;
		try {
			consultation = findOwned(
					organisationId, actorUserId, consultationId);
		}
		catch (ConsultationNotFoundException denied) {
			accessAuditService.record(
					organisationId, actorUserId, "CONSULTATION", consultationId,
					null, null, "DENIED", "NOT_OWNED", requestId);
			throw denied;
		}
		ClinicalRecordResponse response = assembler.assemble(consultation);
		accessAuditService.record(
				organisationId, actorUserId, "CONSULTATION", consultationId,
				consultation.getPatientRegistrationId(),
				consultation.getAppointmentId(), "GRANTED", "AUTHOR", requestId);
		return response;
	}

	@Transactional
	public ClinicalRecordResponse replaceDraft(
			UUID organisationId,
			UUID actorUserId,
			UUID consultationId,
			ReplaceConsultationDraftRequest request,
			String requestId) {
		Consultation consultation = lockOwned(
				organisationId, actorUserId, consultationId);
		requireVersion(consultation, request.version());
		requireStatus(consultation, ConsultationStatus.DRAFT);
		validateSpecialtyMeasurements(request.vitalSigns());
		consultation.replaceNarrativeDraft(
				request.reasonForConsultation(), request.clinicalAssessment(),
				request.treatmentPlan(), request.followUpInstructions(),
				request.additionalNotes(), clock);

		deleteCurrentContent(consultationId);
		symptomRepository.saveAll(indexedSymptoms(consultationId, request.symptoms()));
		historyRepository.saveAll(indexedHistory(consultationId, request.history()));
		if (request.vitalSigns() != null) {
			var value = request.vitalSigns();
			vitalSignsRepository.save(ClinicalVitalSigns.create(
					consultationId, value.measuredAt(), value.temperatureCelsius(),
					value.systolicBloodPressure(), value.diastolicBloodPressure(),
					value.heartRateBpm(), value.respiratoryRateBpm(),
					value.oxygenSaturationPercent(), value.weightKg(), value.heightCm(),
					value.specialtyMeasurements()));
		}
		examinationRepository.saveAll(indexedExaminations(
				consultationId, request.examinationFindings()));
		diagnosisRepository.saveAll(indexedDiagnoses(
				consultationId, request.diagnoses()));
		medicationRepository.saveAll(indexedMedications(
				consultationId, request.medications()));

		Consultation updated = consultationRepository.saveAndFlush(consultation);
		auditRecorder.recordCommand(
				updated, actorUserId,
				ClinicalAuditEventType.CONSULTATION_DRAFT_UPDATED, requestId);
		return assembler.assemble(updated);
	}

	@Transactional
	public ClinicalRecordResponse finalizeRecord(
			UUID organisationId,
			UUID actorUserId,
			UUID consultationId,
			FinalizeConsultationRequest request,
			String requestId) {
		Consultation consultation = lockOwned(
				organisationId, actorUserId, consultationId);
		requireVersion(consultation, request.version());
		requireStatus(consultation, ConsultationStatus.DRAFT);
		ClinicalRecordResponse draft = assembler.assemble(consultation);
		requireComplete(draft);
		consultation.finalizeRecord(actorUserId, clock);
		Consultation finalized = consultationRepository.saveAndFlush(consultation);
		auditRecorder.recordCommand(
				finalized, actorUserId,
				ClinicalAuditEventType.CONSULTATION_FINALIZED, requestId);
		return assembler.assemble(finalized);
	}

	@Transactional
	public ClinicalRecordResponse correct(
			UUID organisationId,
			UUID actorUserId,
			UUID consultationId,
			CreateClinicalCorrectionRequest request,
			String requestId) {
		Consultation consultation = lockOwned(
				organisationId, actorUserId, consultationId);
		requireVersion(consultation, request.version());
		requireStatus(consultation, ConsultationStatus.FINALIZED);
		CorrectionDecision decision = correctionPolicy.decide(
				consultation, request.targetType(), request.targetId(),
				request.fieldName(), request.newValue());
		consultation.recordCorrection(clock);
		Consultation updated = consultationRepository.saveAndFlush(consultation);
		correctionRepository.saveAndFlush(ClinicalCorrection.create(
				updated, actorUserId, request.targetType(), request.targetId(),
				decision.fieldName(), decision.oldValue(), decision.newValue(),
				request.reason(), updated.getVersion(), Instant.now(clock)));
		auditRecorder.recordCommand(
				updated, actorUserId,
				ClinicalAuditEventType.CONSULTATION_CORRECTED, requestId);
		return assembler.assemble(updated);
	}

	private Consultation findOwned(
			UUID organisationId, UUID actorUserId, UUID consultationId) {
		return consultationRepository.findByIdAndOrganisationIdAndDoctorUserId(
				consultationId, organisationId, actorUserId)
				.orElseThrow(ConsultationNotFoundException::new);
	}

	private Consultation lockOwned(
			UUID organisationId, UUID actorUserId, UUID consultationId) {
		return consultationRepository.findOwnedDraftForUpdate(
				consultationId, organisationId, actorUserId)
				.orElseThrow(ConsultationNotFoundException::new);
	}

	private static void requireVersion(Consultation consultation, long version) {
		if (consultation.getVersion() != version) {
			throw new ConcurrentConsultationModificationException();
		}
	}

	private static void requireStatus(
			Consultation consultation, ConsultationStatus requiredStatus) {
		if (consultation.getStatus() != requiredStatus) {
			throw new ConsultationStateConflictException();
		}
	}

	private void deleteCurrentContent(UUID consultationId) {
		symptomRepository.deleteForConsultation(consultationId);
		historyRepository.deleteForConsultation(consultationId);
		vitalSignsRepository.deleteForConsultation(consultationId);
		examinationRepository.deleteForConsultation(consultationId);
		diagnosisRepository.deleteForConsultation(consultationId);
		medicationRepository.deleteForConsultation(consultationId);
	}

	private static List<ClinicalSymptom> indexedSymptoms(
			UUID consultationId,
			List<ReplaceConsultationDraftRequest.SymptomItem> values) {
		return java.util.stream.IntStream.range(0, values.size())
				.mapToObj(index -> {
					var value = values.get(index);
					return ClinicalSymptom.create(
							consultationId, index, value.name(), value.onsetDescription(),
							value.severity(), value.notes());
				})
				.toList();
	}

	private static List<ClinicalHistoryEntry> indexedHistory(
			UUID consultationId,
			List<ReplaceConsultationDraftRequest.HistoryItem> values) {
		return java.util.stream.IntStream.range(0, values.size())
				.mapToObj(index -> {
					var value = values.get(index);
					return ClinicalHistoryEntry.create(
							consultationId, index, value.category(),
							value.description(), value.notes());
				})
				.toList();
	}

	private static List<ClinicalExaminationFinding> indexedExaminations(
			UUID consultationId,
			List<ReplaceConsultationDraftRequest.ExaminationItem> values) {
		return java.util.stream.IntStream.range(0, values.size())
				.mapToObj(index -> {
					var value = values.get(index);
					return ClinicalExaminationFinding.create(
							consultationId, index, value.bodySystem(),
							value.finding(), value.notes());
				})
				.toList();
	}

	private static List<ClinicalDiagnosis> indexedDiagnoses(
			UUID consultationId,
			List<ReplaceConsultationDraftRequest.DiagnosisItem> values) {
		return java.util.stream.IntStream.range(0, values.size())
				.mapToObj(index -> {
					var value = values.get(index);
					return ClinicalDiagnosis.create(
							consultationId, index, value.code(), value.codeSystem(),
							value.label(), value.type(), value.status(), value.notes());
				})
				.toList();
	}

	private static List<ClinicalMedicationItem> indexedMedications(
			UUID consultationId,
			List<ReplaceConsultationDraftRequest.MedicationItem> values) {
		return java.util.stream.IntStream.range(0, values.size())
				.mapToObj(index -> {
					var value = values.get(index);
					return ClinicalMedicationItem.create(
							consultationId, index, value.kind(), value.name(),
							value.strength(), value.form(), value.dosage(),
							value.frequency(), value.route(), value.duration(),
							value.quantity(), value.specialInstructions());
				})
				.toList();
	}

	private static void validateSpecialtyMeasurements(
			ReplaceConsultationDraftRequest.VitalSignsItem vitalSigns) {
		if (vitalSigns == null || vitalSigns.specialtyMeasurements() == null) return;
		for (Map.Entry<String, Object> entry
				: vitalSigns.specialtyMeasurements().entrySet()) {
			if (!SPECIALTY_KEY.matcher(entry.getKey()).matches()
					|| !safeSpecialtyValue(entry.getValue())) {
				throw new IllegalArgumentException("specialtyMeasurements is invalid");
			}
		}
	}

	private static boolean safeSpecialtyValue(Object value) {
		return value == null || value instanceof Number || value instanceof Boolean
				|| value instanceof String text && text.length() <= 500;
	}

	private static void requireComplete(ClinicalRecordResponse record) {
		if (blank(record.reasonForConsultation())
				|| blank(record.clinicalAssessment())
				|| blank(record.followUpInstructions())
				|| record.symptoms().isEmpty()
				|| record.examinationFindings().isEmpty()
				|| record.diagnoses().stream().noneMatch(
						diagnosis -> diagnosis.status() != DiagnosisStatus.RULED_OUT)
				|| blank(record.treatmentPlan()) && record.medications().stream()
						.noneMatch(item -> item.kind() == MedicationKind.PRESCRIBED)
				|| record.medications().stream()
						.filter(item -> item.kind() == MedicationKind.PRESCRIBED)
						.anyMatch(item -> blank(item.dosage())
								|| blank(item.frequency()) || blank(item.route())
								|| blank(item.duration()))) {
			throw new ConsultationIncompleteException();
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
