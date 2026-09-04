ALTER TABLE appointment_audit_event
    DROP CONSTRAINT ck_appointment_audit_event_type;

ALTER TABLE appointment_audit_event
    ADD CONSTRAINT ck_appointment_audit_event_type CHECK (
        event_type IN (
            'APPOINTMENT_BOOKED',
            'APPOINTMENT_CONFIRMED',
            'APPOINTMENT_REJECTED',
            'APPOINTMENT_RESCHEDULED',
            'APPOINTMENT_CANCELLED',
            'APPOINTMENT_CHECKED_IN',
            'APPOINTMENT_STARTED',
            'APPOINTMENT_COMPLETED',
            'APPOINTMENT_NO_SHOW'
        )
    );

ALTER TABLE appointment_outbox_event
    DROP CONSTRAINT ck_appointment_outbox_event_type;

ALTER TABLE appointment_outbox_event
    ADD CONSTRAINT ck_appointment_outbox_event_type CHECK (
        event_type IN (
            'APPOINTMENT_REQUESTED',
            'APPOINTMENT_CONFIRMED',
            'APPOINTMENT_REJECTED',
            'APPOINTMENT_RESCHEDULED',
            'APPOINTMENT_CANCELLED',
            'APPOINTMENT_CHECKED_IN',
            'APPOINTMENT_STARTED',
            'APPOINTMENT_COMPLETED',
            'APPOINTMENT_NO_SHOW'
        )
    );
