CREATE TABLE clinical_consultation (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    appointment_id UUID NOT NULL,
    patient_registration_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    doctor_user_id UUID NOT NULL,
    doctor_membership_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason_for_consultation VARCHAR(1000),
    draft_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_clinical_consultation_appointment UNIQUE (appointment_id),
    CONSTRAINT ck_clinical_consultation_status CHECK (status IN ('DRAFT')),
    CONSTRAINT ck_clinical_consultation_reason CHECK (
        reason_for_consultation IS NULL OR btrim(reason_for_consultation) <> ''
    ),
    CONSTRAINT ck_clinical_consultation_notes CHECK (
        draft_notes IS NULL OR btrim(draft_notes) <> ''
    ),
    CONSTRAINT ck_clinical_consultation_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_clinical_consultation_version CHECK (version >= 0)
);

CREATE INDEX ix_clinical_consultation_org_patient
    ON clinical_consultation (organisation_id, patient_id, created_at DESC, id);

CREATE INDEX ix_clinical_consultation_doctor
    ON clinical_consultation (organisation_id, doctor_user_id, created_at DESC, id);

CREATE TABLE clinical_audit_event (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    consultation_id UUID NOT NULL,
    appointment_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL,
    resource_version BIGINT NOT NULL,
    metadata JSONB NOT NULL,
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_clinical_audit_consultation
        FOREIGN KEY (consultation_id) REFERENCES clinical_consultation (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_clinical_audit_event_type CHECK (
        event_type IN ('CONSULTATION_DRAFT_CREATED', 'CONSULTATION_DRAFT_UPDATED')
    ),
    CONSTRAINT ck_clinical_audit_result CHECK (result IN ('SUCCESS')),
    CONSTRAINT ck_clinical_audit_version CHECK (resource_version >= 0),
    CONSTRAINT ck_clinical_audit_metadata CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT ck_clinical_audit_request CHECK (
        request_id IS NULL OR btrim(request_id) <> ''
    )
);

CREATE INDEX ix_clinical_audit_consultation_occurred
    ON clinical_audit_event (consultation_id, occurred_at DESC, id);

CREATE OR REPLACE FUNCTION reject_clinical_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'clinical_audit_event rows are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER clinical_audit_append_only
BEFORE UPDATE OR DELETE ON clinical_audit_event
FOR EACH ROW EXECUTE FUNCTION reject_clinical_audit_mutation();

CREATE TABLE clinical_outbox_event (
    id UUID PRIMARY KEY,
    audit_event_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publication_attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error_code VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_clinical_outbox_audit
        FOREIGN KEY (audit_event_id) REFERENCES clinical_audit_event (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_clinical_outbox_audit UNIQUE (audit_event_id),
    CONSTRAINT ck_clinical_outbox_event_type CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_clinical_outbox_payload CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_clinical_outbox_attempts CHECK (publication_attempts >= 0),
    CONSTRAINT ck_clinical_outbox_version CHECK (version >= 0)
);

CREATE INDEX ix_clinical_outbox_ready
    ON clinical_outbox_event (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
