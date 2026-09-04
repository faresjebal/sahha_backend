CREATE TABLE appointment_outbox_event (
    id UUID PRIMARY KEY,
    appointment_audit_event_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    appointment_id UUID NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publication_attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error_code VARCHAR(128),
    claim_token UUID,
    claimed_at TIMESTAMPTZ,
    claim_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_appointment_outbox_audit
        FOREIGN KEY (appointment_audit_event_id)
        REFERENCES appointment_audit_event (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_appointment_outbox_appointment
        FOREIGN KEY (appointment_id)
        REFERENCES scheduled_appointment (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_appointment_outbox_audit
        UNIQUE (appointment_audit_event_id),
    CONSTRAINT ck_appointment_outbox_event_type CHECK (
        event_type IN (
            'APPOINTMENT_REQUESTED',
            'APPOINTMENT_CONFIRMED',
            'APPOINTMENT_REJECTED',
            'APPOINTMENT_RESCHEDULED',
            'APPOINTMENT_CANCELLED'
        )
    ),
    CONSTRAINT ck_appointment_outbox_aggregate_version CHECK (
        aggregate_version >= 0
    ),
    CONSTRAINT ck_appointment_outbox_payload CHECK (
        jsonb_typeof(payload) = 'object'
    ),
    CONSTRAINT ck_appointment_outbox_attempts CHECK (
        publication_attempts >= 0
        AND (
            (publication_attempts = 0 AND last_attempt_at IS NULL)
            OR
            (publication_attempts > 0 AND last_attempt_at IS NOT NULL)
        )
    ),
    CONSTRAINT ck_appointment_outbox_publication CHECK (
        published_at IS NULL
        OR (
            published_at >= occurred_at
            AND publication_attempts > 0
            AND last_attempt_at IS NOT NULL
            AND last_error_code IS NULL
            AND claim_token IS NULL
            AND claimed_at IS NULL
            AND claim_until IS NULL
        )
    ),
    CONSTRAINT ck_appointment_outbox_next_attempt CHECK (
        next_attempt_at >= occurred_at
    ),
    CONSTRAINT ck_appointment_outbox_error CHECK (
        last_error_code IS NULL OR btrim(last_error_code) <> ''
    ),
    CONSTRAINT ck_appointment_outbox_claim CHECK (
        (
            claim_token IS NULL
            AND claimed_at IS NULL
            AND claim_until IS NULL
        )
        OR (
            published_at IS NULL
            AND claim_token IS NOT NULL
            AND claimed_at IS NOT NULL
            AND claim_until IS NOT NULL
            AND claim_until > claimed_at
        )
    ),
    CONSTRAINT ck_appointment_outbox_version CHECK (version >= 0)
);

CREATE INDEX ix_appointment_outbox_ready
    ON appointment_outbox_event (next_attempt_at, occurred_at, id)
    WHERE published_at IS NULL;

CREATE INDEX ix_appointment_outbox_claim_expiry
    ON appointment_outbox_event (claim_until)
    WHERE published_at IS NULL AND claim_until IS NOT NULL;
