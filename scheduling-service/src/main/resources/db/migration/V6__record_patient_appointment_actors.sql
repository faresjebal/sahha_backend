ALTER TABLE scheduled_appointment
    ADD COLUMN booked_by_actor_type VARCHAR(16) NOT NULL DEFAULT 'STAFF';

ALTER TABLE scheduled_appointment
    ALTER COLUMN booked_by_membership_id DROP NOT NULL;

ALTER TABLE scheduled_appointment
    ADD CONSTRAINT ck_appointment_booking_actor_type CHECK (
        booked_by_actor_type IN ('STAFF', 'PATIENT')
    ),
    ADD CONSTRAINT ck_appointment_booking_actor_membership CHECK (
        (booked_by_actor_type = 'STAFF' AND booked_by_membership_id IS NOT NULL)
        OR
        (booked_by_actor_type = 'PATIENT' AND booked_by_membership_id IS NULL)
    );

ALTER TABLE scheduled_appointment
    ALTER COLUMN booked_by_actor_type DROP DEFAULT;

ALTER TABLE appointment_audit_event
    ADD COLUMN actor_type VARCHAR(16) NOT NULL DEFAULT 'STAFF';

ALTER TABLE appointment_audit_event
    ALTER COLUMN actor_membership_id DROP NOT NULL;

ALTER TABLE appointment_audit_event
    ADD CONSTRAINT ck_appointment_audit_actor_type CHECK (
        actor_type IN ('STAFF', 'PATIENT')
    ),
    ADD CONSTRAINT ck_appointment_audit_actor_membership CHECK (
        (actor_type = 'STAFF' AND actor_membership_id IS NOT NULL)
        OR
        (actor_type = 'PATIENT' AND actor_membership_id IS NULL)
    );

ALTER TABLE appointment_audit_event
    ALTER COLUMN actor_type DROP DEFAULT;
