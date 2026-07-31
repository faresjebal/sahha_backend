CREATE TABLE security_event (
    id UUID PRIMARY KEY,
    user_id UUID,
    subject_user_id UUID,
    normalized_email VARCHAR(320),
    event_type VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64),
    session_id UUID,
    request_id VARCHAR(128),
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_security_event_user
        FOREIGN KEY (user_id)
        REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_security_event_subject_user
        FOREIGN KEY (subject_user_id)
        REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_security_event_session
        FOREIGN KEY (session_id)
        REFERENCES user_session (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_security_event_email
        CHECK (
            normalized_email IS NULL
            OR (
                btrim(normalized_email) <> ''
                AND normalized_email = lower(btrim(normalized_email))
            )
        ),
    CONSTRAINT ck_security_event_type
        CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_security_event_result
        CHECK (result IN ('SUCCESS', 'DENIED', 'HIGH_RISK')),
    CONSTRAINT ck_security_event_reason
        CHECK (reason_code IS NULL OR btrim(reason_code) <> ''),
    CONSTRAINT ck_security_event_request
        CHECK (request_id IS NULL OR btrim(request_id) <> ''),
    CONSTRAINT ck_security_event_ip
        CHECK (ip_address IS NULL OR btrim(ip_address) <> ''),
    CONSTRAINT ck_security_event_user_agent
        CHECK (user_agent IS NULL OR btrim(user_agent) <> '')
);

CREATE INDEX ix_security_event_user_occurred
    ON security_event (user_id, occurred_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX ix_security_event_subject_user_occurred
    ON security_event (subject_user_id, occurred_at DESC)
    WHERE subject_user_id IS NOT NULL;

CREATE INDEX ix_security_event_session_occurred
    ON security_event (session_id, occurred_at DESC)
    WHERE session_id IS NOT NULL;

CREATE INDEX ix_security_event_type_occurred
    ON security_event (event_type, occurred_at DESC);

CREATE OR REPLACE FUNCTION reject_security_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'security_event rows are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER security_event_append_only
BEFORE UPDATE OR DELETE ON security_event
FOR EACH ROW
EXECUTE FUNCTION reject_security_event_mutation();

CREATE TABLE auth_outbox_event (
    id UUID PRIMARY KEY,
    security_event_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publication_attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error_code VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_auth_outbox_security_event
        FOREIGN KEY (security_event_id)
        REFERENCES security_event (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_auth_outbox_security_event
        UNIQUE (security_event_id),
    CONSTRAINT ck_auth_outbox_event_type
        CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_auth_outbox_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_auth_outbox_attempts
        CHECK (publication_attempts >= 0),
    CONSTRAINT ck_auth_outbox_publication
        CHECK (
            (
                published_at IS NULL
            )
            OR (
                published_at >= occurred_at
                AND publication_attempts > 0
                AND last_attempt_at IS NOT NULL
            )
        ),
    CONSTRAINT ck_auth_outbox_last_attempt
        CHECK (
            last_attempt_at IS NULL
            OR last_attempt_at >= occurred_at
        ),
    CONSTRAINT ck_auth_outbox_next_attempt
        CHECK (next_attempt_at >= occurred_at),
    CONSTRAINT ck_auth_outbox_error
        CHECK (last_error_code IS NULL OR btrim(last_error_code) <> ''),
    CONSTRAINT ck_auth_outbox_version
        CHECK (version >= 0)
);

CREATE INDEX ix_auth_outbox_ready
    ON auth_outbox_event (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
