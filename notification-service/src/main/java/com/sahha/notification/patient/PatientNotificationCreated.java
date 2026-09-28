package com.sahha.notification.patient;

import java.util.UUID;
import com.sahha.notification.dto.response.NotificationResponse;

public record PatientNotificationCreated(UUID patientId, UUID organisationId, NotificationResponse notification) { }
