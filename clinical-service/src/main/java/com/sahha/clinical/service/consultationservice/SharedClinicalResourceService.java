package com.sahha.clinical.service.consultationservice;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.clinical.client.communication.CommunicationShareAccessClient;
import com.sahha.clinical.dto.response.ClinicalRecordResponse;
import com.sahha.clinical.dto.response.SharedClinicalResourceResponse;
import com.sahha.clinical.entity.ClinicalHistoryCategory;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.exception.SharingContextUnavailableException;
import com.sahha.clinical.mapper.ClinicalRecordAssembler;
import com.sahha.clinical.repository.ConsultationRepository;
import com.sahha.clinical.service.clinicalauditservice.ClinicalAccessAuditService;

@Service
public class SharedClinicalResourceService {
    private static final Set<String> TYPES = Set.of("CONSULTATION", "DIAGNOSIS", "MEDICATION", "ALLERGY");
    private final ConsultationRepository repository;
    private final CommunicationShareAccessClient sharingClient;
    private final ClinicalRecordAssembler assembler;
    private final ClinicalAccessAuditService audit;

    public SharedClinicalResourceService(ConsultationRepository repository,
            CommunicationShareAccessClient sharingClient, ClinicalRecordAssembler assembler,
            ClinicalAccessAuditService audit) {
        this.repository = repository;
        this.sharingClient = sharingClient;
        this.assembler = assembler;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SharedClinicalResourceResponse read(UUID organisationId, UUID actorId,
            UUID patientId, String type, UUID resourceId, String token, String requestId) {
        if (!TYPES.contains(type)) throw new ConsultationNotFoundException();
        try {
            var owner = repository.findSharedResourceOwner(organisationId, patientId, type, resourceId)
                    .orElseThrow(ConsultationNotFoundException::new);
            // Ownership comes from Clinical's database, never from caller assertions.
            Instant validUntil = sharingClient.requireGrant(patientId, type, resourceId,
                    owner.getDoctorUserId(), owner.getDoctorMembershipId(), token, requestId);
            ClinicalRecordResponse record = assembler.assemble(owner);
            var response = switch (type) {
                case "CONSULTATION" -> new SharedClinicalResourceResponse(type, resourceId,
                        patientId, validUntil, record, null, null, null);
                case "DIAGNOSIS" -> new SharedClinicalResourceResponse(type, resourceId,
                        patientId, validUntil, null, record.diagnoses().stream()
                                .filter(item -> resourceId.equals(item.id())).findFirst()
                                .orElseThrow(ConsultationNotFoundException::new), null, null);
                case "MEDICATION" -> new SharedClinicalResourceResponse(type, resourceId,
                        patientId, validUntil, null, null, record.medications().stream()
                                .filter(item -> resourceId.equals(item.id())).findFirst()
                                .orElseThrow(ConsultationNotFoundException::new), null);
                case "ALLERGY" -> new SharedClinicalResourceResponse(type, resourceId,
                        patientId, validUntil, null, null, null, record.history().stream()
                                .filter(item -> resourceId.equals(item.id())
                                        && item.category() == ClinicalHistoryCategory.ALLERGY)
                                .findFirst().orElseThrow(ConsultationNotFoundException::new));
                default -> throw new ConsultationNotFoundException();
            };
            audit.record(organisationId, actorId, type, resourceId, patientId,
                    null, "GRANTED", "SELECTED_SHARE", requestId);
            return response;
        }
        catch (ConsultationNotFoundException | SharingContextUnavailableException denied) {
            audit.record(organisationId, actorId, type, resourceId, null,
                    null, "DENIED", denied instanceof SharingContextUnavailableException
                            ? "SHARING_UNAVAILABLE" : "SHARE_OR_OWNERSHIP_DENIED", requestId);
            throw denied;
        }
    }
}
