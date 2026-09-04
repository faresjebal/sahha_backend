ALTER TABLE in_app_notification
    DROP CONSTRAINT fk_in_app_notification_event,
    DROP CONSTRAINT ck_in_app_notification_type,
    DROP CONSTRAINT ck_in_app_notification_resource,
    DROP CONSTRAINT ck_in_app_notification_status,
    DROP CONSTRAINT ck_in_app_notification_period,
    DROP CONSTRAINT ck_in_app_notification_text;

ALTER TABLE in_app_notification
    ALTER COLUMN appointment_status DROP NOT NULL,
    ALTER COLUMN appointment_starts_at DROP NOT NULL,
    ALTER COLUMN appointment_ends_at DROP NOT NULL,
    ALTER COLUMN appointment_time_zone DROP NOT NULL,
    ALTER COLUMN appointment_location_label DROP NOT NULL;

ALTER TABLE in_app_notification
    ADD CONSTRAINT ck_in_app_notification_type CHECK (
        notification_type IN (
            'APPOINTMENT_REQUESTED',
            'APPOINTMENT_RESCHEDULED',
            'APPOINTMENT_CANCELLED',
            'PATIENT_CHECKED_IN',
            'MESSAGE_RECEIVED'
        )
    ),
    ADD CONSTRAINT ck_in_app_notification_resource CHECK (
        resource_type IN ('APPOINTMENT', 'CONVERSATION')
    ),
    ADD CONSTRAINT ck_in_app_notification_shape CHECK (
        (
            resource_type = 'APPOINTMENT'
            AND notification_type IN (
                'APPOINTMENT_REQUESTED',
                'APPOINTMENT_RESCHEDULED',
                'APPOINTMENT_CANCELLED',
                'PATIENT_CHECKED_IN'
            )
            AND appointment_status IS NOT NULL
            AND appointment_starts_at IS NOT NULL
            AND appointment_ends_at IS NOT NULL
            AND appointment_ends_at > appointment_starts_at
            AND appointment_time_zone IS NOT NULL
            AND btrim(appointment_time_zone) <> ''
            AND appointment_location_label IS NOT NULL
            AND btrim(appointment_location_label) <> ''
        )
        OR
        (
            resource_type = 'CONVERSATION'
            AND notification_type = 'MESSAGE_RECEIVED'
            AND appointment_status IS NULL
            AND appointment_starts_at IS NULL
            AND appointment_ends_at IS NULL
            AND appointment_time_zone IS NULL
            AND appointment_location_label IS NULL
        )
    );

CREATE TABLE consumed_communication_event (
    event_id UUID PRIMARY KEY,
    source_topic VARCHAR(249) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    conversation_id UUID NOT NULL,
    message_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    sender_user_id UUID NOT NULL,
    resource_version BIGINT NOT NULL,
    event_occurred_at TIMESTAMPTZ NOT NULL,
    recipient_count INTEGER NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_consumed_communication_source
        UNIQUE (source_topic, source_partition, source_offset),
    CONSTRAINT ck_consumed_communication_source CHECK (
        btrim(source_topic) <> ''
        AND source_partition >= 0
        AND source_offset >= 0
    ),
    CONSTRAINT ck_consumed_communication_type CHECK (
        event_type = 'MESSAGE_SENT'
    ),
    CONSTRAINT ck_consumed_communication_version CHECK (
        resource_version >= 0
        AND recipient_count > 0
        AND recipient_count <= 20
    ),
    CONSTRAINT ck_consumed_communication_outcome CHECK (
        outcome IN ('PROCESSING', 'NOTIFICATION_CREATED')
    )
);

CREATE INDEX ix_consumed_communication_conversation
    ON consumed_communication_event (
        organisation_id,
        conversation_id,
        event_occurred_at DESC
    );

CREATE TABLE rejected_communication_event (
    id UUID PRIMARY KEY,
    source_topic VARCHAR(249) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    rejected_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_rejected_communication_source
        UNIQUE (source_topic, source_partition, source_offset),
    CONSTRAINT ck_rejected_communication_source CHECK (
        btrim(source_topic) <> ''
        AND source_partition >= 0
        AND source_offset >= 0
    ),
    CONSTRAINT ck_rejected_communication_hash CHECK (
        payload_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_rejected_communication_reason CHECK (
        btrim(reason_code) <> ''
    )
);

CREATE INDEX ix_rejected_communication_time
    ON rejected_communication_event (rejected_at DESC, id);
