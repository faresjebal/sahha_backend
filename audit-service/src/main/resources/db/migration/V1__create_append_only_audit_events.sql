-- Metadata only: no clinical content, raw event payload, arbitrary JSON or tokens.
-- External identifiers are references, never cross-service foreign keys.
CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    source_service VARCHAR(40) NOT NULL,
    source_event_id UUID NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    organisation_id UUID,
    actor_user_id UUID,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID,
    patient_id UUID,
    result VARCHAR(16) NOT NULL,
    access_reason_code VARCHAR(64),
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uq_audit_event_source UNIQUE (source_service, source_event_id),
    CONSTRAINT ck_audit_event_source CHECK (source_service IN (
        'auth-service', 'organisation-service', 'patient-service',
        'scheduling-service', 'clinical-service', 'communication-service',
        'notification-service', 'file-service', 'audit-service'
    )),
    CONSTRAINT ck_audit_event_scope CHECK (
        (scope = 'GLOBAL' AND organisation_id IS NULL AND patient_id IS NULL)
        OR (scope = 'ORGANISATION' AND organisation_id IS NOT NULL)
    ),
    CONSTRAINT ck_audit_event_type CHECK (event_type ~ '^[A-Z][A-Z0-9_]{0,95}$'),
    CONSTRAINT ck_audit_event_resource CHECK (resource_type ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT ck_audit_event_result CHECK (result IN ('SUCCESS', 'DENIED', 'FAILURE')),
    CONSTRAINT ck_audit_event_reason CHECK (
        access_reason_code IS NULL OR access_reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
    ),
    CONSTRAINT ck_audit_event_request CHECK (
        request_id IS NULL OR request_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
    )
);

CREATE INDEX ix_audit_event_organisation_occurred
    ON audit_event (organisation_id, occurred_at DESC, id);
CREATE INDEX ix_audit_event_resource_occurred
    ON audit_event (organisation_id, resource_type, resource_id, occurred_at DESC, id);
CREATE INDEX ix_audit_event_actor_occurred
    ON audit_event (actor_user_id, occurred_at DESC, id);

CREATE FUNCTION reject_audit_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

-- Statement level also rejects empty-table/no-op mutations and TRUNCATE.
CREATE TRIGGER audit_event_append_only
BEFORE UPDATE OR DELETE OR TRUNCATE ON audit_event
FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_event_mutation();
