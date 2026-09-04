CREATE TABLE patient_account_link (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    auth_user_id UUID NOT NULL,
    linked_by_user_id UUID NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_patient_account_link_identity
        FOREIGN KEY (patient_id)
        REFERENCES patient_identity (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_patient_account_link_patient UNIQUE (patient_id),
    CONSTRAINT uq_patient_account_link_auth_user UNIQUE (auth_user_id),
    CONSTRAINT ck_patient_account_link_actor CHECK (
        linked_by_user_id = auth_user_id
    ),
    CONSTRAINT ck_patient_account_link_timestamps CHECK (
        updated_at >= linked_at
    ),
    CONSTRAINT ck_patient_account_link_version CHECK (version >= 0)
);

ALTER TABLE patient_audit_event
    DROP CONSTRAINT ck_patient_audit_resource_type;

ALTER TABLE patient_audit_event
    ADD CONSTRAINT ck_patient_audit_resource_type CHECK (
        resource_type IN (
            'PATIENT_DIRECTORY',
            'PATIENT_REGISTRATION',
            'PATIENT_ACCOUNT_LINK'
        )
    );
