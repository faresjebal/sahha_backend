package com.sahha.notification.patient;

import java.util.UUID;

/** Verified by Patient, never accepted from a notification request body. */
public record PatientNotificationContext(UUID registrationId, UUID patientId,
        UUID organisationId, String registrationStatus, UUID authUserId) {
    public String principalName() {
        return "patient__" + patientId + "__" + organisationId;
    }
}
