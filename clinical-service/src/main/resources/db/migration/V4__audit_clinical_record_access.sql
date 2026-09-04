CREATE TABLE clinical_access_audit_event (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID NOT NULL,
    patient_registration_id UUID,
    care_appointment_id UUID,
    result VARCHAR(16) NOT NULL,
    access_reason VARCHAR(64) NOT NULL,
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_clinical_access_resource_type CHECK (
        resource_type IN ('CONSULTATION', 'PATIENT_SUMMARY')
    ),
    CONSTRAINT ck_clinical_access_result CHECK (
        result IN ('GRANTED', 'DENIED')
    ),
    CONSTRAINT ck_clinical_access_reason CHECK (btrim(access_reason) <> ''),
    CONSTRAINT ck_clinical_access_request CHECK (
        request_id IS NULL OR btrim(request_id) <> ''
    )
);

CREATE INDEX ix_clinical_access_actor
    ON clinical_access_audit_event (
        organisation_id, actor_user_id, occurred_at DESC, id
    );

CREATE INDEX ix_clinical_access_resource
    ON clinical_access_audit_event (
        resource_type, resource_id, occurred_at DESC, id
    );

CREATE OR REPLACE FUNCTION reject_clinical_access_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'clinical_access_audit_event rows are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER clinical_access_audit_append_only
BEFORE UPDATE OR DELETE ON clinical_access_audit_event
FOR EACH ROW EXECUTE FUNCTION reject_clinical_access_audit_mutation();
