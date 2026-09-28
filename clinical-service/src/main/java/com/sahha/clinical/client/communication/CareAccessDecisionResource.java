package com.sahha.clinical.client.communication;

import java.time.Instant;
import java.util.UUID;

public record CareAccessDecisionResource(Boolean allowed, UUID organisationId,
        UUID patientRegistrationId, UUID doctorUserId, UUID grantId, UUID referralId,
        Instant validUntil) { }
