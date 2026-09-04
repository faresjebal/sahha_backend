CREATE TABLE consumed_appointment_event (
    event_id UUID PRIMARY KEY,
    source_topic VARCHAR(249) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    appointment_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    resource_version BIGINT NOT NULL,
    event_occurred_at TIMESTAMPTZ NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_consumed_appointment_source
        UNIQUE (source_topic, source_partition, source_offset),
    CONSTRAINT ck_consumed_appointment_source CHECK (
        btrim(source_topic) <> ''
        AND source_partition >= 0
        AND source_offset >= 0
    ),
    CONSTRAINT ck_consumed_appointment_event_type CHECK (
        event_type IN (
            'APPOINTMENT_REQUESTED',
            'APPOINTMENT_CONFIRMED',
            'APPOINTMENT_REJECTED',
            'APPOINTMENT_RESCHEDULED',
            'APPOINTMENT_CANCELLED'
        )
    ),
    CONSTRAINT ck_consumed_appointment_version CHECK (
        resource_version >= 0
    ),
    CONSTRAINT ck_consumed_appointment_outcome CHECK (
        outcome IN (
            'PROCESSING',
            'NOTIFICATION_CREATED',
            'NO_ELIGIBLE_RECIPIENT',
            'STALE'
        )
    )
);

CREATE INDEX ix_consumed_appointment_aggregate
    ON consumed_appointment_event (
        organisation_id,
        appointment_id,
        resource_version DESC
    );

CREATE TABLE rejected_appointment_event (
    id UUID PRIMARY KEY,
    source_topic VARCHAR(249) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    rejected_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_rejected_appointment_source
        UNIQUE (source_topic, source_partition, source_offset),
    CONSTRAINT ck_rejected_appointment_source CHECK (
        btrim(source_topic) <> ''
        AND source_partition >= 0
        AND source_offset >= 0
    ),
    CONSTRAINT ck_rejected_appointment_hash CHECK (
        payload_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_rejected_appointment_reason CHECK (
        btrim(reason_code) <> ''
    )
);

CREATE INDEX ix_rejected_appointment_time
    ON rejected_appointment_event (rejected_at DESC, id);

CREATE TABLE appointment_notification_cursor (
    appointment_id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    last_event_id UUID NOT NULL,
    last_resource_version BIGINT NOT NULL,
    last_event_occurred_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_appointment_notification_cursor_event
        FOREIGN KEY (last_event_id)
        REFERENCES consumed_appointment_event (event_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_appointment_notification_cursor_version CHECK (
        last_resource_version >= 0
        AND version >= 0
    )
);

CREATE INDEX ix_appointment_notification_cursor_organisation
    ON appointment_notification_cursor (organisation_id, appointment_id);

CREATE TABLE in_app_notification (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    notification_type VARCHAR(48) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID NOT NULL,
    appointment_status VARCHAR(24) NOT NULL,
    appointment_starts_at TIMESTAMPTZ NOT NULL,
    appointment_ends_at TIMESTAMPTZ NOT NULL,
    appointment_time_zone VARCHAR(64) NOT NULL,
    appointment_location_label VARCHAR(160) NOT NULL,
    resource_version BIGINT NOT NULL,
    event_occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_in_app_notification_event
        FOREIGN KEY (source_event_id)
        REFERENCES consumed_appointment_event (event_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_in_app_notification_recipient
        UNIQUE (source_event_id, recipient_user_id),
    CONSTRAINT ck_in_app_notification_type CHECK (
        notification_type IN (
            'APPOINTMENT_REQUESTED',
            'APPOINTMENT_RESCHEDULED',
            'APPOINTMENT_CANCELLED'
        )
    ),
    CONSTRAINT ck_in_app_notification_resource CHECK (
        resource_type = 'APPOINTMENT'
    ),
    CONSTRAINT ck_in_app_notification_status CHECK (
        appointment_status IN (
            'REQUESTED',
            'RESCHEDULED',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_in_app_notification_period CHECK (
        appointment_ends_at > appointment_starts_at
    ),
    CONSTRAINT ck_in_app_notification_text CHECK (
        btrim(appointment_time_zone) <> ''
        AND btrim(appointment_location_label) <> ''
    ),
    CONSTRAINT ck_in_app_notification_version CHECK (
        resource_version >= 0
        AND version >= 0
    ),
    CONSTRAINT ck_in_app_notification_read CHECK (
        read_at IS NULL OR read_at >= created_at
    )
);

CREATE INDEX ix_in_app_notification_recipient_unread
    ON in_app_notification (recipient_user_id, created_at DESC, id)
    WHERE read_at IS NULL;

CREATE INDEX ix_in_app_notification_recipient_history
    ON in_app_notification (recipient_user_id, created_at DESC, id);
