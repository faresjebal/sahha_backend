package com.sahha.clinical.service.consultationservice;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.clinical.client.organisation.OrganisationDoctorClient;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.exception.OrganisationContextUnavailableException;
import com.sahha.clinical.repository.ConsultationRepository;
import com.sahha.clinical.service.clinicalauditservice.ClinicalAccessAuditService;

/** A signed doctor claim and historical authorship are not a live membership. */
@Service
public class ConsultationAuthorAccessService {
    private final ConsultationRepository consultations;
    private final OrganisationDoctorClient doctors;
    private final ClinicalAccessAuditService audit;

    public ConsultationAuthorAccessService(ConsultationRepository consultations,
            OrganisationDoctorClient doctors, ClinicalAccessAuditService audit) {
        this.consultations = consultations;
        this.doctors = doctors;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public void require(UUID organisationId, UUID actorId, UUID consultationId,
            String token, String requestId) {
        var record = consultations.findByIdAndOrganisationIdAndDoctorUserId(
                consultationId, organisationId, actorId).orElse(null);
        if (record == null) {
            audit.record(organisationId, actorId, "CONSULTATION", consultationId,
                    null, null, "DENIED", "AUTHOR_RESOURCE_NOT_FOUND", requestId);
            throw new ConsultationNotFoundException();
        }
        try {
            UUID membership = doctors.requireCurrentMembership(organisationId, actorId, token, requestId);
            if (!record.getDoctorMembershipId().equals(membership)) throw new ClinicalAccessDeniedException();
        } catch (ClinicalAccessDeniedException | OrganisationContextUnavailableException denied) {
            audit.record(organisationId, actorId, "CONSULTATION", consultationId,
                    record.getPatientRegistrationId(), record.getAppointmentId(),
                    "DENIED", "AUTHOR_MEMBERSHIP_NOT_AUTHORISED", requestId);
            throw denied;
        }
        audit.record(organisationId, actorId, "CONSULTATION", consultationId,
                record.getPatientRegistrationId(), record.getAppointmentId(),
                "GRANTED", "AUTHOR_MEMBERSHIP_VERIFIED", requestId);
    }
}
