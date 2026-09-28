package com.sahha.notification.patient;

import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import com.sahha.notification.config.NotificationWebSocketConfiguration;
import com.sahha.notification.dto.response.RealtimeNotificationMessage;

@Component
public class PatientNotificationDelivery {
    private final SimpMessagingTemplate messaging;
    public PatientNotificationDelivery(SimpMessagingTemplate messaging) { this.messaging = messaging; }
    @TransactionalEventListener
    public void deliver(PatientNotificationCreated event) {
        try {
            messaging.convertAndSendToUser("patient__" + event.patientId() + "__" + event.organisationId(),
                    NotificationWebSocketConfiguration.DELIVERY_DESTINATION,
                    RealtimeNotificationMessage.created(event.notification()));
        } catch (RuntimeException unavailable) {
            LoggerFactory.getLogger(getClass()).warn("Patient live delivery unavailable; REST recovery required");
        }
    }
}
