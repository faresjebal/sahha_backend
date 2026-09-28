package com.sahha.notification.patient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.sahha.notification.dto.response.*;
import com.sahha.notification.entity.NotificationType;
import com.sahha.notification.event.AppointmentEventV1;
import com.sahha.notification.event.AppointmentStatus;
import com.sahha.notification.exception.NotificationNotFoundException;

@Repository
public class PatientNotificationRepository {
    private final JdbcTemplate jdbc;
    public PatientNotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public NotificationResponse create(AppointmentEventV1 event, Instant now) {
        var type = event.eventType().name().equals("APPOINTMENT_CHECKED_IN")
                ? NotificationType.PATIENT_CHECKED_IN : NotificationType.valueOf(event.eventType().name());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO patient_appointment_notification
                (id, source_event_id, patient_id, organisation_id, appointment_id,
                 notification_type, appointment_status, starts_at, ends_at, time_zone,
                 location_label, resource_version, occurred_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, event.eventId(), event.patientId(), event.organisationId(), event.appointmentId(),
                type.name(), event.status().name(), Timestamp.from(event.startsAt()), Timestamp.from(event.endsAt()),
                event.timeZone(), event.locationLabel(), event.resourceVersion(),
                Timestamp.from(event.occurredAt()), Timestamp.from(now));
        return new NotificationResponse(id, type, "APPOINTMENT", event.appointmentId(), event.status(),
                event.startsAt(), event.endsAt(), event.timeZone(), event.locationLabel(), event.resourceVersion(),
                event.occurredAt(), now, false, null);
    }

    public NotificationPageResponse list(PatientNotificationContext context, int page, int size) {
        long count = jdbc.queryForObject("""
                SELECT count(*) FROM patient_appointment_notification WHERE patient_id = ? AND organisation_id = ?
                """, Long.class, context.patientId(), context.organisationId());
        var items = jdbc.query("""
                SELECT * FROM patient_appointment_notification WHERE patient_id = ? AND organisation_id = ?
                ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, PatientNotificationRepository::map, context.patientId(), context.organisationId(), size, (long) page * size);
        return new NotificationPageResponse(items, page, size, count, (int) ((count + size - 1) / size),
                page == 0, ((long) page + 1) * size >= count);
    }

    public long unread(PatientNotificationContext context) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM patient_appointment_notification
                WHERE patient_id = ? AND organisation_id = ? AND read_at IS NULL
                """, Long.class, context.patientId(), context.organisationId());
    }

    public NotificationResponse markRead(PatientNotificationContext context, UUID id, Instant now) {
        jdbc.update("""
                UPDATE patient_appointment_notification SET read_at = ?
                WHERE patient_id = ? AND organisation_id = ? AND id = ? AND read_at IS NULL
                """, Timestamp.from(now), context.patientId(), context.organisationId(), id);
        return jdbc.query("""
                SELECT * FROM patient_appointment_notification WHERE patient_id = ? AND organisation_id = ? AND id = ?
                """, PatientNotificationRepository::map, context.patientId(), context.organisationId(), id)
                .stream().findFirst().orElseThrow(NotificationNotFoundException::new);
    }

    public MarkAllNotificationsReadResponse markAllRead(PatientNotificationContext context, Instant now) {
        int count = jdbc.update("""
                UPDATE patient_appointment_notification SET read_at = ?
                WHERE patient_id = ? AND organisation_id = ? AND read_at IS NULL
                """, Timestamp.from(now), context.patientId(), context.organisationId());
        return new MarkAllNotificationsReadResponse(count, now);
    }

    private static NotificationResponse map(ResultSet rs, int row) throws SQLException {
        var readAt = rs.getTimestamp("read_at");
        return new NotificationResponse(rs.getObject("id", UUID.class),
                NotificationType.valueOf(rs.getString("notification_type")), "APPOINTMENT",
                rs.getObject("appointment_id", UUID.class), AppointmentStatus.valueOf(rs.getString("appointment_status")),
                rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                rs.getString("time_zone"), rs.getString("location_label"), rs.getLong("resource_version"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("created_at").toInstant(),
                readAt != null, readAt == null ? null : readAt.toInstant());
    }
}
