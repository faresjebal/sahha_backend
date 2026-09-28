package com.sahha.notification.integration;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import com.sahha.notification.patient.*;
import com.sahha.notification.event.*;
import com.sahha.notification.exception.NotificationNotFoundException;
import com.sahha.notification.repository.InAppNotificationRepository;
import com.sahha.notification.service.appointmentnotificationservice.*;

@SpringBootTest
@Transactional
class PatientNotificationProjectionIntegrationTests {
    @Autowired AppointmentNotificationService service;
    @Autowired PatientNotificationRepository patients;
    @Autowired InAppNotificationRepository staff;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    private final Instant now = Instant.parse("2027-01-01T09:00:00Z");

    @Test
    void allNineStatusesCreateMinimalPatientUpdatesWithoutStaffSelfAlerts() throws Exception {
        var owner = owner();
        long version = 0;
        var appointment = UUID.randomUUID();
        for (var type : AppointmentEventType.values()) {
            var event = event(owner, appointment, type, version++);
            assertEquals(AppointmentEventProcessingResult.NOTIFICATION_CREATED, consume(event));
            assertEquals(AppointmentEventProcessingResult.DUPLICATE, consume(event));
        }
        var page = patients.list(owner, 0, 20);
        assertEquals(9, page.totalElements());
        assertEquals(9, patients.unread(owner));
        assertEquals(0, staff.count());
        for (var item : page.items()) {
            assertEquals("APPOINTMENT", item.resourceType());
            assertEquals(appointment, item.resourceId());
            String json = mapper.writeValueAsString(item);
            assertFalse(json.contains(owner.patientId().toString()));
            assertFalse(json.contains(owner.authUserId().toString()));
            assertFalse(json.contains(owner.organisationId().toString()));
            assertFalse(json.contains("sourceEventId"));
            assertFalse(json.contains("clinical"));
        }
    }

    @Test
    void equalAndOlderVersionsCannotDuplicatePatientUpdates() {
        var owner = owner();
        UUID appointment = UUID.randomUUID();
        consume(event(owner, appointment, AppointmentEventType.APPOINTMENT_RESCHEDULED, 3));
        assertEquals(AppointmentEventProcessingResult.STALE,
                consume(event(owner, appointment, AppointmentEventType.APPOINTMENT_CONFIRMED, 1)));
        assertEquals(AppointmentEventProcessingResult.STALE,
                consume(event(owner, appointment, AppointmentEventType.APPOINTMENT_RESCHEDULED, 3)));
        assertEquals(1, patients.list(owner, 0, 20).totalElements());
    }

    @Test
    void patientAndOrganisationScopesIsolateListCountAndReadWrites() {
        var owner = owner();
        var otherPatient = new PatientNotificationContext(UUID.randomUUID(), UUID.randomUUID(),
                owner.organisationId(), "ACTIVE", UUID.randomUUID());
        var otherOrganisation = new PatientNotificationContext(UUID.randomUUID(), owner.patientId(),
                UUID.randomUUID(), "ACTIVE", owner.authUserId());
        for (var context : java.util.List.of(owner, otherPatient, otherOrganisation)) {
            consume(event(context, UUID.randomUUID(), AppointmentEventType.APPOINTMENT_CONFIRMED, 1));
        }
        var ownId = patients.list(owner, 0, 20).items().getFirst().id();
        assertThrows(NotificationNotFoundException.class, () -> patients.markRead(otherPatient, ownId, now));
        assertThrows(NotificationNotFoundException.class, () -> patients.markRead(otherOrganisation, ownId, now));
        assertEquals(1, patients.markAllRead(owner, now).updatedCount());
        assertEquals(0, patients.unread(owner));
        assertEquals(1, patients.unread(otherPatient));
        assertEquals(1, patients.unread(otherOrganisation));
        assertEquals(1, patients.list(owner, 0, 20).items().size());
    }

    @Test
    void readAcknowledgementsAreIdempotentAndPagesRecoverPersistedState() {
        var owner = owner();
        for (int index = 0; index < 3; index++) {
            consume(event(owner, UUID.randomUUID(), AppointmentEventType.APPOINTMENT_REQUESTED, 0));
        }
        var page = patients.list(owner, 0, 2);
        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertFalse(page.last());
        assertEquals(1, patients.list(owner, 1, 2).items().size());
        UUID id = page.items().getFirst().id();
        assertEquals(now, patients.markRead(owner, id, now).readAt());
        assertEquals(now, patients.markRead(owner, id, now.plusSeconds(100)).readAt());
        assertEquals(2, patients.unread(owner));
        assertEquals(2, patients.markAllRead(owner, now.plusSeconds(200)).updatedCount());
        assertEquals(0, patients.markAllRead(owner, now.plusSeconds(300)).updatedCount());
        assertTrue(patients.list(owner, 0, 20).items().stream().allMatch(item -> item.read()));
    }

    @Test
    void failedTransactionLeavesNeitherInboxNorConsumedLedgerEntry() {
        var owner = owner();
        var event = event(owner, UUID.randomUUID(), AppointmentEventType.APPOINTMENT_CONFIRMED, 1);
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            consume(event);
            throw new IllegalStateException("Synthetic rollback check");
        }));
        assertEquals(0, patients.unread(owner));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM consumed_appointment_event WHERE event_id = ?", Long.class, event.eventId()));
    }

    @Test
    void databaseRejectsReassignmentOfNotificationOwnership() {
        var owner = owner();
        consume(event(owner, UUID.randomUUID(), AppointmentEventType.APPOINTMENT_CONFIRMED, 1));
        UUID id = patients.list(owner, 0, 20).items().getFirst().id();
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE patient_appointment_notification SET patient_id = ? WHERE id = ?", UUID.randomUUID(), id));
    }

    @Test
    void databaseRejectsUndoingAcknowledgedReadTime() {
        var owner = owner();
        consume(event(owner, UUID.randomUUID(), AppointmentEventType.APPOINTMENT_CONFIRMED, 1));
        UUID id = patients.list(owner, 0, 20).items().getFirst().id();
        patients.markRead(owner, id, now);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE patient_appointment_notification SET read_at = NULL WHERE id = ?", id));
    }

    private PatientNotificationContext owner() {
        return new PatientNotificationContext(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ACTIVE", UUID.randomUUID());
    }

    private AppointmentEventProcessingResult consume(AppointmentEventV1 event) {
        return service.consume(event, new AppointmentEventSource("patient-test-" + event.eventId(), 0, 0));
    }

    private AppointmentEventV1 event(PatientNotificationContext owner, UUID appointment, AppointmentEventType type, long version) {
        var status = switch (type) {
            case APPOINTMENT_STARTED -> AppointmentStatus.IN_PROGRESS;
            default -> AppointmentStatus.valueOf(type.name().substring("APPOINTMENT_".length()));
        };
        UUID doctor = UUID.randomUUID();
        return new AppointmentEventV1(UUID.randomUUID(), type, 1, now, appointment, owner.organisationId(),
                owner.patientId(), doctor, doctor, "patient-notification-projection-test", status,
                now.plusSeconds(3600), now.plusSeconds(5400), "UTC", "Synthetic room", version,
                version == 0 ? null : AppointmentStatus.REQUESTED, version == 0 ? null : now.plusSeconds(3600));
    }
}
