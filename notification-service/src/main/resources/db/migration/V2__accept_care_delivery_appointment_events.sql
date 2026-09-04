ALTER TABLE consumed_appointment_event
    DROP CONSTRAINT ck_consumed_appointment_event_type;

ALTER TABLE consumed_appointment_event
    ADD CONSTRAINT ck_consumed_appointment_event_type CHECK (
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

ALTER TABLE in_app_notification
    DROP CONSTRAINT ck_in_app_notification_type,
    DROP CONSTRAINT ck_in_app_notification_status;

ALTER TABLE in_app_notification
    ADD CONSTRAINT ck_in_app_notification_type CHECK (
        notification_type IN (
            'APPOINTMENT_REQUESTED',
            'APPOINTMENT_RESCHEDULED',
            'APPOINTMENT_CANCELLED',
            'PATIENT_CHECKED_IN'
        )
    ),
    ADD CONSTRAINT ck_in_app_notification_status CHECK (
        appointment_status IN (
            'REQUESTED',
            'RESCHEDULED',
            'CANCELLED',
            'CHECKED_IN'
        )
    );
