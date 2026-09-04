CREATE TABLE consumed_clinical_event (
    event_id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    appointment_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    clinical_resource_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_topic VARCHAR(255),
    source_partition INTEGER,
    source_offset BIGINT,
    outcome VARCHAR(24) NOT NULL,
    previous_status VARCHAR(24),
    resulting_status VARCHAR(24),
    appointment_version BIGINT,
    conflict_code VARCHAR(64),

    CONSTRAINT uq_consumed_clinical_source
        UNIQUE (source_topic, source_partition, source_offset),
    CONSTRAINT ck_consumed_clinical_event_type CHECK (
        event_type = 'consultation.finalised.v1'
    ),
    CONSTRAINT ck_consumed_clinical_version CHECK (
        clinical_resource_version >= 0
        AND (appointment_version IS NULL OR appointment_version >= 0)
    ),
    CONSTRAINT ck_consumed_clinical_source CHECK (
        (source_type = 'REST' AND source_topic IS NULL
            AND source_partition IS NULL AND source_offset IS NULL)
        OR
        (source_type = 'KAFKA' AND source_topic IS NOT NULL
            AND source_partition >= 0 AND source_offset >= 0)
    ),
    CONSTRAINT ck_consumed_clinical_outcome CHECK (
        outcome IN ('APPLIED', 'IDEMPOTENT', 'CONFLICT')
    ),
    CONSTRAINT ck_consumed_clinical_status CHECK (
        previous_status IS NULL OR previous_status IN (
            'REQUESTED', 'CONFIRMED', 'REJECTED', 'RESCHEDULED',
            'CANCELLED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW'
        )
    ),
    CONSTRAINT ck_consumed_clinical_resulting_status CHECK (
        resulting_status IS NULL OR resulting_status IN (
            'REQUESTED', 'CONFIRMED', 'REJECTED', 'RESCHEDULED',
            'CANCELLED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW'
        )
    ),
    CONSTRAINT ck_consumed_clinical_result CHECK (
        (outcome = 'APPLIED' AND previous_status = 'IN_PROGRESS'
            AND resulting_status = 'COMPLETED'
            AND appointment_version IS NOT NULL AND conflict_code IS NULL)
        OR
        (outcome = 'IDEMPOTENT' AND previous_status = 'COMPLETED'
            AND resulting_status = 'COMPLETED'
            AND appointment_version IS NOT NULL AND conflict_code IS NULL)
        OR
        (outcome = 'CONFLICT' AND conflict_code IS NOT NULL)
    ),
    CONSTRAINT ck_consumed_clinical_times CHECK (processed_at >= occurred_at)
);

CREATE INDEX ix_consumed_clinical_consultation
    ON consumed_clinical_event (consultation_id, processed_at DESC, event_id);

CREATE INDEX ix_consumed_clinical_appointment
    ON consumed_clinical_event (appointment_id, processed_at DESC, event_id);
