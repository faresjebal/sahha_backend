ALTER TABLE scheduled_appointment
    ADD COLUMN status_reason VARCHAR(500);

ALTER TABLE scheduled_appointment
    ADD CONSTRAINT ck_appointment_status_reason CHECK (
        (status IN ('REJECTED', 'RESCHEDULED', 'CANCELLED')
            AND status_reason IS NOT NULL
            AND btrim(status_reason) <> '')
        OR
        (status NOT IN ('REJECTED', 'RESCHEDULED', 'CANCELLED')
            AND status_reason IS NULL)
    );

ALTER TABLE appointment_audit_event
    ADD COLUMN command_request_id UUID,
    ADD COLUMN transition_reason VARCHAR(500),
    ADD COLUMN previous_starts_at TIMESTAMPTZ,
    ADD COLUMN new_starts_at TIMESTAMPTZ;

UPDATE appointment_audit_event audit
SET command_request_id = appointment.booking_request_id,
    new_starts_at = appointment.starts_at
FROM scheduled_appointment appointment
WHERE appointment.id = audit.appointment_id;

ALTER TABLE appointment_audit_event
    ALTER COLUMN command_request_id SET NOT NULL,
    ALTER COLUMN new_starts_at SET NOT NULL;

ALTER TABLE appointment_audit_event
    DROP CONSTRAINT ck_appointment_audit_event_type;

ALTER TABLE appointment_audit_event
    ADD CONSTRAINT ck_appointment_audit_event_type CHECK (
        event_type IN (
            'APPOINTMENT_BOOKED',
            'APPOINTMENT_CONFIRMED',
            'APPOINTMENT_REJECTED',
            'APPOINTMENT_RESCHEDULED',
            'APPOINTMENT_CANCELLED'
        )
    ),
    ADD CONSTRAINT ck_appointment_audit_transition_reason CHECK (
        transition_reason IS NULL OR btrim(transition_reason) <> ''
    ),
    ADD CONSTRAINT ck_appointment_audit_period CHECK (
        previous_starts_at IS NULL OR new_starts_at IS NOT NULL
    ),
    ADD CONSTRAINT uq_appointment_audit_command_request
        UNIQUE (organisation_id, command_request_id);
