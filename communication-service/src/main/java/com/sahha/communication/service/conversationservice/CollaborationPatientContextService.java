package com.sahha.communication.service.conversationservice;

import java.util.UUID;
import org.springframework.stereotype.Service;
import com.sahha.communication.client.clinical.ClinicalCollaborationSourceClient;
import com.sahha.communication.client.scheduling.SchedulingPatientContextClient;

/** An explicit authored source is not a continuing-care or record-read entitlement. */
@Service
public class CollaborationPatientContextService {
    private final SchedulingPatientContextClient scheduling;
    private final ClinicalCollaborationSourceClient clinical;
    public CollaborationPatientContextService(SchedulingPatientContextClient scheduling,
            ClinicalCollaborationSourceClient clinical) {
        this.scheduling = scheduling; this.clinical = clinical;
    }
    public void requireMentionable(UUID organisationId, UUID patientRegistrationId,
            UUID actorId, String token, UUID sourceConsultationId) {
        if (sourceConsultationId == null) scheduling.requireMentionable(organisationId, patientRegistrationId, actorId, token);
        else clinical.requireFinalizedSource(organisationId, patientRegistrationId, actorId, sourceConsultationId, token);
        // Never fall back from a denied/unavailable explicit source to another authority.
    }
}
