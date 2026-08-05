CREATE TABLE patient_identity (
    id UUID PRIMARY KEY,
    first_name VARCHAR(80) NOT NULL,
    normalized_first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    normalized_last_name VARCHAR(80) NOT NULL,
    date_of_birth DATE NOT NULL,
    sex VARCHAR(24) NOT NULL,
    identifier_type VARCHAR(24),
    identifier_fingerprint CHAR(64),
    identifier_last_four VARCHAR(4),
    identifier_country_code CHAR(2),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_patient_identity_names CHECK (
        btrim(first_name) <> ''
        AND btrim(last_name) <> ''
        AND normalized_first_name = lower(btrim(normalized_first_name))
        AND normalized_last_name = lower(btrim(normalized_last_name))
    ),
    CONSTRAINT ck_patient_identity_birth CHECK (date_of_birth <= CURRENT_DATE),
    CONSTRAINT ck_patient_identity_sex CHECK (
        sex IN ('FEMALE', 'MALE', 'INTERSEX', 'UNDISCLOSED')
    ),
    CONSTRAINT ck_patient_identifier_complete CHECK (
        (identifier_type IS NULL
            AND identifier_fingerprint IS NULL
            AND identifier_last_four IS NULL
            AND identifier_country_code IS NULL)
        OR
        (identifier_type IN ('NATIONAL_ID', 'PASSPORT')
            AND identifier_fingerprint ~ '^[0-9a-f]{64}$'
            AND identifier_last_four ~ '^[A-Z0-9]{1,4}$'
            AND identifier_country_code ~ '^[A-Z]{2}$')
    ),
    CONSTRAINT ck_patient_identity_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_patient_identity_version CHECK (version >= 0),
    CONSTRAINT uq_patient_strong_identifier
        UNIQUE (identifier_type, identifier_fingerprint)
);

CREATE INDEX ix_patient_identity_name_birth
    ON patient_identity (
        normalized_last_name,
        normalized_first_name,
        date_of_birth,
        id
    );

CREATE TABLE patient_organisation_registration (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    medical_record_number VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    normalized_phone_number VARCHAR(32) NOT NULL,
    email VARCHAR(254),
    normalized_email VARCHAR(254),
    address VARCHAR(300) NOT NULL,
    city VARCHAR(100),
    region VARCHAR(100),
    postal_code VARCHAR(20),
    country_code CHAR(2) NOT NULL,
    emergency_contact_name VARCHAR(160),
    emergency_contact_phone VARCHAR(32),
    emergency_contact_relationship VARCHAR(80),
    preferred_language VARCHAR(64),
    accessibility_needs VARCHAR(500),
    privacy_notice_acknowledged_at TIMESTAMPTZ NOT NULL,
    registered_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_patient_registration_identity
        FOREIGN KEY (patient_id)
        REFERENCES patient_identity (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_patient_registration_organisation_patient
        UNIQUE (organisation_id, patient_id),
    CONSTRAINT uq_patient_registration_medical_record
        UNIQUE (organisation_id, medical_record_number),
    CONSTRAINT uq_patient_registration_id_organisation
        UNIQUE (id, organisation_id),
    CONSTRAINT ck_patient_registration_record_number
        CHECK (medical_record_number ~ '^PT-[0-9A-F]{12}$'),
    CONSTRAINT ck_patient_registration_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_patient_registration_phone CHECK (
        btrim(phone_number) <> ''
        AND btrim(normalized_phone_number) <> ''
    ),
    CONSTRAINT ck_patient_registration_email CHECK (
        (email IS NULL AND normalized_email IS NULL)
        OR (
            btrim(email) <> ''
            AND normalized_email = lower(btrim(normalized_email))
        )
    ),
    CONSTRAINT ck_patient_registration_address CHECK (btrim(address) <> ''),
    CONSTRAINT ck_patient_registration_location CHECK (
        (city IS NULL OR btrim(city) <> '')
        AND (region IS NULL OR btrim(region) <> '')
        AND (postal_code IS NULL OR btrim(postal_code) <> '')
        AND country_code ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT ck_patient_registration_emergency_contact CHECK (
        (emergency_contact_name IS NULL
            AND emergency_contact_phone IS NULL
            AND emergency_contact_relationship IS NULL)
        OR (emergency_contact_name IS NOT NULL
            AND btrim(emergency_contact_name) <> ''
            AND emergency_contact_phone IS NOT NULL
            AND btrim(emergency_contact_phone) <> '')
    ),
    CONSTRAINT ck_patient_registration_optional_fields CHECK (
        (preferred_language IS NULL OR btrim(preferred_language) <> '')
        AND (accessibility_needs IS NULL OR btrim(accessibility_needs) <> '')
    ),
    CONSTRAINT ck_patient_registration_timestamps CHECK (
        privacy_notice_acknowledged_at >= created_at
        AND updated_at >= created_at
    ),
    CONSTRAINT ck_patient_registration_version CHECK (version >= 0)
);

CREATE INDEX ix_patient_registration_organisation_record
    ON patient_organisation_registration (
        organisation_id,
        status,
        medical_record_number,
        id
    );

CREATE INDEX ix_patient_registration_phone
    ON patient_organisation_registration (normalized_phone_number, patient_id);

CREATE INDEX ix_patient_registration_email
    ON patient_organisation_registration (normalized_email, patient_id)
    WHERE normalized_email IS NOT NULL;

CREATE TABLE patient_audit_event (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID NOT NULL,
    patient_id UUID,
    event_type VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL,
    resource_version BIGINT,
    metadata JSONB NOT NULL,
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_patient_audit_identity
        FOREIGN KEY (patient_id)
        REFERENCES patient_identity (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_patient_audit_resource_type CHECK (
        resource_type IN ('PATIENT_DIRECTORY', 'PATIENT_REGISTRATION')
    ),
    CONSTRAINT ck_patient_audit_event_type CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_patient_audit_result CHECK (result IN ('SUCCESS', 'DENIED')),
    CONSTRAINT ck_patient_audit_version CHECK (
        resource_version IS NULL OR resource_version >= 0
    ),
    CONSTRAINT ck_patient_audit_metadata CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT ck_patient_audit_request CHECK (
        request_id IS NULL OR btrim(request_id) <> ''
    )
);

CREATE INDEX ix_patient_audit_organisation_occurred
    ON patient_audit_event (organisation_id, occurred_at DESC, id);

CREATE INDEX ix_patient_audit_resource_occurred
    ON patient_audit_event (resource_type, resource_id, occurred_at DESC, id);

CREATE OR REPLACE FUNCTION reject_patient_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'patient_audit_event rows are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER patient_audit_append_only
BEFORE UPDATE OR DELETE ON patient_audit_event
FOR EACH ROW
EXECUTE FUNCTION reject_patient_audit_mutation();

CREATE TABLE patient_outbox_event (
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

    CONSTRAINT fk_patient_outbox_audit
        FOREIGN KEY (audit_event_id)
        REFERENCES patient_audit_event (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_patient_outbox_audit UNIQUE (audit_event_id),
    CONSTRAINT ck_patient_outbox_event_type CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_patient_outbox_payload CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_patient_outbox_attempts CHECK (publication_attempts >= 0),
    CONSTRAINT ck_patient_outbox_publication CHECK (
        published_at IS NULL
        OR (
            published_at >= occurred_at
            AND publication_attempts > 0
            AND last_attempt_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_patient_outbox_last_attempt CHECK (
        last_attempt_at IS NULL OR last_attempt_at >= occurred_at
    ),
    CONSTRAINT ck_patient_outbox_next_attempt CHECK (next_attempt_at >= occurred_at),
    CONSTRAINT ck_patient_outbox_error CHECK (
        last_error_code IS NULL OR btrim(last_error_code) <> ''
    ),
    CONSTRAINT ck_patient_outbox_version CHECK (version >= 0)
);

CREATE INDEX ix_patient_outbox_ready
    ON patient_outbox_event (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
