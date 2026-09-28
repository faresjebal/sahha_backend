package com.sahha.notification.repository;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.sahha.notification.event.CommunicationEventSource;
import com.sahha.notification.event.ReferralEventV1;

/** Atomic, Notification-owned inbox processing; participates in the service transaction. */
@Repository
public class ReferralNotificationProjection {
    private final JdbcTemplate jdbc;
    public ReferralNotificationProjection(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean claim(ReferralEventV1 event, CommunicationEventSource source, Instant now) {
        return jdbc.update("""
                INSERT INTO consumed_referral_event
                (event_id, source_topic, source_partition, source_offset, event_type,
                 organisation_id, referral_id, resource_version, event_occurred_at,
                 recipient_count, outcome, processed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROCESSING', ?)
                ON CONFLICT DO NOTHING
                """, event.eventId(), source.topic(), source.partition(), source.offset(),
                event.eventType(), event.organisationId(), event.referralId(), event.resourceVersion(),
                Timestamp.from(event.occurredAt()), event.recipientUserIds().size(), Timestamp.from(now)) == 1;
    }

    public boolean advance(ReferralEventV1 event) {
        return jdbc.update("""
                INSERT INTO referral_notification_cursor (organisation_id, referral_id, last_resource_version)
                VALUES (?, ?, ?)
                ON CONFLICT (organisation_id, referral_id) DO UPDATE
                SET last_resource_version = EXCLUDED.last_resource_version
                WHERE referral_notification_cursor.last_resource_version < EXCLUDED.last_resource_version
                """, event.organisationId(), event.referralId(), event.resourceVersion()) == 1;
    }

    public void finish(ReferralEventV1 event, boolean created) {
        jdbc.update("UPDATE consumed_referral_event SET outcome = ? WHERE event_id = ? AND outcome = 'PROCESSING'",
                created ? "NOTIFICATION_CREATED" : "STALE", event.eventId());
    }
}
