package com.sahha.clinical.service.consultationservice;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.clinical.client.communication.CommunicationCareAccessClient;
import com.sahha.clinical.dto.response.SharedCareHistoryPageResponse;
import com.sahha.clinical.dto.response.SharedCareRecordResponse;
import com.sahha.clinical.dto.response.SharedCareAttachmentContextResponse;
import com.sahha.clinical.entity.ConsultationStatus;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.exception.SharingContextUnavailableException;
import com.sahha.clinical.mapper.ClinicalRecordAssembler;
import com.sahha.clinical.repository.ConsultationRepository;
import com.sahha.clinical.service.clinicalauditservice.ClinicalAccessAuditService;

@Service
public class SharedCareClinicalService {
    private final ConsultationRepository consultations;
    private final CommunicationCareAccessClient care;
    private final ClinicalRecordAssembler assembler;
    private final ClinicalAccessAuditService audit;
    private final Clock clock;

    public SharedCareClinicalService(ConsultationRepository consultations, CommunicationCareAccessClient care,
            ClinicalRecordAssembler assembler, ClinicalAccessAuditService audit, Clock clock) {
        this.consultations = consultations; this.care = care; this.assembler = assembler;
        this.audit = audit; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SharedCareHistoryPageResponse list(UUID organisationId, UUID actorId, UUID patientRegistrationId,
            String token, String requestId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("invalid page");
        try {
            Instant validUntil = care.requireCare(organisationId, actorId, patientRegistrationId, token, requestId);
            var records = consultations.findAllByOrganisationIdAndPatientRegistrationIdAndStatusOrderByFinalizedAtDescIdDesc(
                    organisationId, patientRegistrationId, ConsultationStatus.FINALIZED, PageRequest.of(page, size));
            var content = records.stream().map(record -> new SharedCareHistoryPageResponse.Encounter(record.getId(),
                    record.getAppointmentId(), record.getDoctorUserId(), record.getFinalizedAt(), record.getVersion())).toList();
            requireNotExpired(validUntil);
            audit.record(organisationId, actorId, "CARE_HISTORY", patientRegistrationId, patientRegistrationId,
                    null, "GRANTED", "SHARED_TREATMENT", requestId);
            return new SharedCareHistoryPageResponse(organisationId, patientRegistrationId, validUntil, content,
                    records.getNumber(), records.getSize(), records.getTotalElements(), records.getTotalPages());
        }
        catch (ConsultationNotFoundException | SharingContextUnavailableException denied) {
            denied(organisationId, actorId, "CARE_HISTORY", patientRegistrationId, requestId, denied);
            throw denied;
        }
    }

    @Transactional(readOnly = true)
    public SharedCareRecordResponse read(UUID organisationId, UUID actorId, UUID patientRegistrationId,
            UUID consultationId, String token, String requestId) {
        try {
            // Clinical-owned lookup enforces organisation, patient and finality before returning any content.
            var record = consultations.findSharedResourceOwner(organisationId, patientRegistrationId,
                    "CONSULTATION", consultationId).orElseThrow(ConsultationNotFoundException::new);
            Instant validUntil = care.requireCare(organisationId, actorId, record.getPatientRegistrationId(), token, requestId);
            var content = assembler.assemble(record); // effective values AND attributable correction history
            requireNotExpired(validUntil);
            audit.record(organisationId, actorId, "CONSULTATION", consultationId, record.getPatientRegistrationId(),
                    null, "GRANTED", "SHARED_TREATMENT", requestId);
            return new SharedCareRecordResponse(organisationId, record.getPatientRegistrationId(), validUntil, content);
        }
        catch (ConsultationNotFoundException | SharingContextUnavailableException denied) {
            denied(organisationId, actorId, "CONSULTATION", consultationId, requestId, denied);
            throw denied;
        }
    }

    @Transactional(readOnly = true)
    public SharedCareAttachmentContextResponse attachmentContext(UUID organisationId, UUID actorId,
            UUID patientRegistrationId, UUID consultationId, String token, String requestId) {
        try {
            var record = consultations.findSharedResourceOwner(organisationId, patientRegistrationId,
                    "CONSULTATION", consultationId).orElseThrow(ConsultationNotFoundException::new);
            Instant validUntil = care.requireCare(organisationId, actorId, record.getPatientRegistrationId(), token, requestId);
            requireNotExpired(validUntil);
            audit.record(organisationId, actorId, "CONSULTATION", consultationId, record.getPatientRegistrationId(),
                    null, "GRANTED", "SHARED_CARE_ATTACHMENTS", requestId);
            return new SharedCareAttachmentContextResponse(organisationId, record.getPatientRegistrationId(),
                    record.getPatientId(), record.getId(), actorId, record.getStatus().name(), validUntil);
        }
        catch (ConsultationNotFoundException | SharingContextUnavailableException denied) {
            denied(organisationId, actorId, "CONSULTATION", consultationId, requestId, denied);
            throw denied;
        }
    }

    private void requireNotExpired(Instant validUntil) {
        if (validUntil == null || !clock.instant().isBefore(validUntil)) throw new ConsultationNotFoundException();
    }
    private void denied(UUID organisationId, UUID actorId, String type, UUID id, String requestId, RuntimeException cause) {
        audit.record(organisationId, actorId, type, id, null, null, "DENIED",
                cause instanceof SharingContextUnavailableException ? "SHARING_UNAVAILABLE" : "CARE_OR_OWNERSHIP_DENIED", requestId);
    }
}
