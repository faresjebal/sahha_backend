CREATE TABLE organisation (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) NOT NULL,
    legal_name VARCHAR(200),
    type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    address VARCHAR(300) NOT NULL,
    city VARCHAR(100) NOT NULL,
    region VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20),
    country_code CHAR(2) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_organisation_normalized_name
        UNIQUE (normalized_name),
    CONSTRAINT ck_organisation_name
        CHECK (btrim(name) <> ''),
    CONSTRAINT ck_organisation_normalized_name
        CHECK (
            btrim(normalized_name) <> ''
            AND normalized_name = lower(btrim(normalized_name))
        ),
    CONSTRAINT ck_organisation_legal_name
        CHECK (legal_name IS NULL OR btrim(legal_name) <> ''),
    CONSTRAINT ck_organisation_type
        CHECK (type IN ('HOSPITAL', 'CLINIC', 'PRIVATE_PRACTICE')),
    CONSTRAINT ck_organisation_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_organisation_contact_email
        CHECK (
            btrim(contact_email) <> ''
            AND contact_email = lower(btrim(contact_email))
        ),
    CONSTRAINT ck_organisation_phone
        CHECK (btrim(phone_number) <> ''),
    CONSTRAINT ck_organisation_address
        CHECK (btrim(address) <> ''),
    CONSTRAINT ck_organisation_city
        CHECK (btrim(city) <> ''),
    CONSTRAINT ck_organisation_region
        CHECK (btrim(region) <> ''),
    CONSTRAINT ck_organisation_postal_code
        CHECK (postal_code IS NULL OR btrim(postal_code) <> ''),
    CONSTRAINT ck_organisation_country
        CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_organisation_time_zone
        CHECK (btrim(time_zone) <> ''),
    CONSTRAINT ck_organisation_timestamps
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_organisation_version
        CHECK (version >= 0)
);

CREATE INDEX ix_organisation_type_status_name
    ON organisation (type, status, normalized_name);

CREATE INDEX ix_organisation_status_name
    ON organisation (status, normalized_name);

CREATE TABLE organisation_audit_event (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL,
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_organisation_audit_organisation
        FOREIGN KEY (organisation_id)
        REFERENCES organisation (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_organisation_audit_type
        CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_organisation_audit_result
        CHECK (result IN ('SUCCESS', 'DENIED')),
    CONSTRAINT ck_organisation_audit_request
        CHECK (request_id IS NULL OR btrim(request_id) <> '')
);

CREATE INDEX ix_organisation_audit_organisation_occurred
    ON organisation_audit_event (organisation_id, occurred_at DESC);

CREATE INDEX ix_organisation_audit_actor_occurred
    ON organisation_audit_event (actor_user_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION reject_organisation_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'organisation_audit_event rows are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER organisation_audit_append_only
BEFORE UPDATE OR DELETE ON organisation_audit_event
FOR EACH ROW
EXECUTE FUNCTION reject_organisation_audit_mutation();

CREATE TABLE organisation_outbox_event (
    id UUID PRIMARY KEY,
    audit_event_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publication_attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error_code VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_organisation_outbox_audit
        FOREIGN KEY (audit_event_id)
        REFERENCES organisation_audit_event (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_organisation_outbox_organisation
        FOREIGN KEY (organisation_id)
        REFERENCES organisation (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_organisation_outbox_audit
        UNIQUE (audit_event_id),
    CONSTRAINT ck_organisation_outbox_type
        CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_organisation_outbox_payload
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_organisation_outbox_attempts
        CHECK (publication_attempts >= 0),
    CONSTRAINT ck_organisation_outbox_publication
        CHECK (
            published_at IS NULL
            OR (
                published_at >= occurred_at
                AND publication_attempts > 0
                AND last_attempt_at IS NOT NULL
            )
        ),
    CONSTRAINT ck_organisation_outbox_last_attempt
        CHECK (
            last_attempt_at IS NULL
            OR last_attempt_at >= occurred_at
        ),
    CONSTRAINT ck_organisation_outbox_next_attempt
        CHECK (next_attempt_at >= occurred_at),
    CONSTRAINT ck_organisation_outbox_error
        CHECK (last_error_code IS NULL OR btrim(last_error_code) <> ''),
    CONSTRAINT ck_organisation_outbox_version
        CHECK (version >= 0)
);

CREATE INDEX ix_organisation_outbox_ready
    ON organisation_outbox_event (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
