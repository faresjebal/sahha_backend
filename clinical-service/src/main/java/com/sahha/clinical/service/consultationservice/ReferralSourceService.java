package com.sahha.clinical.service.consultationservice;

import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.clinical.client.organisation.OrganisationDoctorClient;
import com.sahha.clinical.dto.response.ReferralSourcePageResponse;
import com.sahha.clinical.entity.ConsultationStatus;
import com.sahha.clinical.repository.ConsultationRepository;
import com.sahha.clinical.service.clinicalauditservice.ClinicalAccessAuditService;

@Service
public class ReferralSourceService {
    private final ConsultationRepository consultations;
    private final OrganisationDoctorClient doctors;
    private final ClinicalAccessAuditService audit;
    public ReferralSourceService(ConsultationRepository consultations, OrganisationDoctorClient doctors,
            ClinicalAccessAuditService audit) {
        this.consultations = consultations; this.doctors = doctors; this.audit = audit;
    }
    @Transactional(readOnly = true)
    public ReferralSourcePageResponse list(UUID organisationId, UUID actorId, String token,
            String requestId, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Invalid referral source page");
        }
        UUID membershipId = doctors.requireCurrentMembership(organisationId, actorId, token, requestId);
        var result = consultations
                .findByOrganisationIdAndDoctorUserIdAndDoctorMembershipIdAndStatusOrderByFinalizedAtDescIdDesc(
                        organisationId, actorId, membershipId, ConsultationStatus.FINALIZED, PageRequest.of(page, size));
        var content = result.getContent().stream().map(record -> {
            audit.record(organisationId, actorId, "CONSULTATION", record.getId(),
                    record.getPatientRegistrationId(), record.getAppointmentId(),
                    "GRANTED", "AUTHOR_REFERRAL_SOURCE", requestId);
            return new ReferralSourcePageResponse.Source(record.getId(), record.getPatientRegistrationId(),
                    record.getAppointmentId(), record.getFinalizedAt(), record.getVersion());
        }).toList();
        return new ReferralSourcePageResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
