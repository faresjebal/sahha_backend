package com.sahha.notification.integration;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.sahha.notification.event.CommunicationEventSource;
import com.sahha.notification.event.ReferralEventV1;
import com.sahha.notification.repository.InAppNotificationRepository;
import com.sahha.notification.service.referralnotificationservice.ReferralNotificationService;
import com.sahha.notification.service.referralnotificationservice.ReferralNotificationService.Result;

@SpringBootTest
@Transactional
class ReferralNotificationIntegrationTests {
    @Autowired ReferralNotificationService service;
    @Autowired InAppNotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private final String topic = "referral-test-" + UUID.randomUUID();
    private long offset;
    private CommunicationEventSource source() { return new CommunicationEventSource(topic, 0, offset++); }
    private ReferralEventV1 event(String type, String status, UUID organisation, UUID referral, long version, List<UUID> recipients) {
        return new ReferralEventV1(UUID.randomUUID(), "referral." + type + ".v1", 1, Instant.now(),
                organisation, referral, type.equals("expired") ? new UUID(0, 0) : UUID.randomUUID(), recipients, status, version);
    }
    @ParameterizedTest @CsvSource({"sent,SENT", "accepted,ACTIVE", "rejected,REJECTED",
            "revoked,REVOKED", "completed,COMPLETED", "expired,EXPIRED"})
    void storesOnlyPrivateRoutingMetadataAndDeduplicates(String type, String status) {
        var recipients = type.equals("expired") ? List.of(UUID.randomUUID(), UUID.randomUUID()) : List.of(UUID.randomUUID());
        var event = event(type, status, UUID.randomUUID(), UUID.randomUUID(), 3, recipients);
        assertEquals(Result.NOTIFICATIONS_CREATED, service.consume(event, source()));
        assertEquals(Result.DUPLICATE, service.consume(event, source()));
        notifications.flush();
        for (var recipient : recipients) {
            var rows = notifications.findAllByRecipientUserIdOrderByCreatedAtDesc(recipient);
            assertEquals(1, rows.size());
            var notification = rows.getFirst();
            assertEquals("REFERRAL", notification.getResourceType());
            assertEquals(event.referralId(), notification.getResourceId());
            assertEquals(event.organisationId(), notification.getOrganisationId());
            assertEquals(event.notificationType(), notification.getNotificationType());
            assertNull(notification.getAppointmentStatus());
            assertNull(notification.getAppointmentStartsAt());
            assertNull(notification.getAppointmentLocationLabel());
        }
        assertEquals("NOTIFICATION_CREATED", jdbc.queryForObject(
                "SELECT outcome FROM consumed_referral_event WHERE event_id = ?", String.class, event.eventId()));
    }
    @Test void olderAndEqualVersionsAreRecordedWithoutNewAlertsAndOtherReferralsRemainIndependent() {
        var org = UUID.randomUUID(); var referral = UUID.randomUUID(); var recipients = List.of(UUID.randomUUID());
        assertEquals(Result.NOTIFICATIONS_CREATED, service.consume(event("revoked", "REVOKED", org, referral, 4, recipients), source()));
        var old = event("sent", "SENT", org, referral, 0, recipients);
        assertEquals(Result.STALE, service.consume(old, source()));
        assertEquals(Result.STALE, service.consume(event("accepted", "ACTIVE", org, referral, 4, recipients), source()));
        assertEquals(Result.NOTIFICATIONS_CREATED, service.consume(event("sent", "SENT", org, UUID.randomUUID(), 0, recipients), source()));
        assertEquals(2, notifications.findAllByRecipientUserIdOrderByCreatedAtDesc(recipients.getFirst()).size());
        assertEquals("STALE", jdbc.queryForObject("SELECT outcome FROM consumed_referral_event WHERE event_id = ?", String.class, old.eventId()));
    }
    @Test void rollbackRemovesClaimCursorAndNotificationsSoRedeliveryCanSucceed() {
        var event = event("sent", "SENT", UUID.randomUUID(), UUID.randomUUID(), 0, List.of(UUID.randomUUID()));
        var tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            service.consume(event, source()); notifications.flush();
            throw new IllegalStateException("Synthetic processing interruption");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM consumed_referral_event WHERE event_id = ?", Integer.class, event.eventId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM referral_notification_cursor WHERE referral_id = ?", Integer.class, event.referralId()));
        assertTrue(notifications.findAllByRecipientUserIdOrderByCreatedAtDesc(event.recipientUserIds().getFirst()).isEmpty());
        assertEquals(Result.NOTIFICATIONS_CREATED, service.consume(event, source()));
    }
    @Test void completedConsumptionEvidenceCannotBeRewrittenOrDeleted() {
        var event = event("sent", "SENT", UUID.randomUUID(), UUID.randomUUID(), 0, List.of(UUID.randomUUID()));
        service.consume(event, source());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "DELETE FROM consumed_referral_event WHERE event_id = ?", event.eventId()));
    }
}
