package com.sahha.communication.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A live read authority, not permission to edit another doctor's records. */
public record CareAccessDecisionResponse(boolean allowed, UUID organisationId,
        UUID patientRegistrationId, UUID doctorUserId, UUID grantId, UUID referralId,
        Instant validUntil) {
    public static CareAccessDecisionResponse denied() {
        return new CareAccessDecisionResponse(false, null, null, null, null, null, null);
    }
}
