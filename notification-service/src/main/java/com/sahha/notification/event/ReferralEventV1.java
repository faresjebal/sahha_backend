package com.sahha.notification.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.sahha.notification.entity.NotificationType;

/** Routing metadata only; never a clinical-access credential. */
public record ReferralEventV1(UUID eventId, String eventType, Integer schemaVersion,
        Instant occurredAt, UUID organisationId, UUID referralId, UUID actorUserId,
        List<UUID> recipientUserIds, String status, Long resourceVersion) {
    public NotificationType notificationType() {
        return switch (eventType) {
            case "referral.sent.v1" -> NotificationType.REFERRAL_RECEIVED;
            case "referral.accepted.v1" -> NotificationType.REFERRAL_ACCEPTED;
            case "referral.rejected.v1" -> NotificationType.REFERRAL_REJECTED;
            case "referral.revoked.v1" -> NotificationType.REFERRAL_REVOKED;
            case "referral.completed.v1" -> NotificationType.REFERRAL_COMPLETED;
            case "referral.expired.v1" -> NotificationType.REFERRAL_EXPIRED;
            default -> throw new InvalidCommunicationEventException("UNSUPPORTED_EVENT_TYPE");
        };
    }
    public String expectedStatus() {
        return switch (notificationType()) {
            case REFERRAL_RECEIVED -> "SENT";
            case REFERRAL_ACCEPTED -> "ACTIVE";
            case REFERRAL_REJECTED -> "REJECTED";
            case REFERRAL_REVOKED -> "REVOKED";
            case REFERRAL_COMPLETED -> "COMPLETED";
            case REFERRAL_EXPIRED -> "EXPIRED";
            default -> throw new InvalidCommunicationEventException("UNSUPPORTED_EVENT_TYPE");
        };
    }
}
