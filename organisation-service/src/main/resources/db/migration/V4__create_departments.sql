CREATE TABLE department (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    code VARCHAR(32) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(24) NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_department_organisation
        FOREIGN KEY (organisation_id)
        REFERENCES organisation (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_department_organisation_name
        UNIQUE (organisation_id, normalized_name),
    CONSTRAINT uq_department_organisation_code
        UNIQUE (organisation_id, code),
    CONSTRAINT ck_department_name
        CHECK (btrim(name) <> ''),
    CONSTRAINT ck_department_normalized_name
        CHECK (
            btrim(normalized_name) <> ''
            AND normalized_name = lower(btrim(normalized_name))
        ),
    CONSTRAINT ck_department_code
        CHECK (
            code = upper(btrim(code))
            AND code ~ '^[A-Z][A-Z0-9_-]{1,31}$'
        ),
    CONSTRAINT ck_department_description
        CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT ck_department_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_department_timestamps
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_department_version
        CHECK (version >= 0)
);

CREATE INDEX ix_department_organisation_status_name
    ON department (organisation_id, status, normalized_name, id);

ALTER TABLE organisation_audit_event
    DROP CONSTRAINT ck_organisation_audit_resource_type,
    ADD CONSTRAINT ck_organisation_audit_resource_type
        CHECK (resource_type IN ('ORGANISATION', 'MEMBERSHIP', 'DEPARTMENT'));
