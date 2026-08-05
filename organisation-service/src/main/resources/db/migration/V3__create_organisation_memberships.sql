CREATE TABLE organisation_membership (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    email_snapshot VARCHAR(254) NOT NULL,
    display_name_snapshot VARCHAR(201) NOT NULL,
    status VARCHAR(24) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_membership_organisation
        FOREIGN KEY (organisation_id)
        REFERENCES organisation (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_membership_organisation_user
        UNIQUE (organisation_id, user_id),
    CONSTRAINT ck_membership_email_snapshot
        CHECK (
            btrim(email_snapshot) <> ''
            AND email_snapshot = lower(btrim(email_snapshot))
        ),
    CONSTRAINT ck_membership_display_name
        CHECK (btrim(display_name_snapshot) <> ''),
    CONSTRAINT ck_membership_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REMOVED')),
    CONSTRAINT ck_membership_timestamps
        CHECK (
            joined_at >= created_at
            AND updated_at >= created_at
        ),
    CONSTRAINT ck_membership_version
        CHECK (version >= 0)
);

CREATE INDEX ix_membership_organisation_status_name
    ON organisation_membership (
        organisation_id,
        status,
        display_name_snapshot,
        id
    );

CREATE INDEX ix_membership_user_status
    ON organisation_membership (user_id, status);

CREATE TABLE organisation_membership_role (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL,
    role VARCHAR(40) NOT NULL,
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL,
    deactivated_at TIMESTAMPTZ,

    CONSTRAINT fk_membership_role_membership
        FOREIGN KEY (membership_id)
        REFERENCES organisation_membership (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_membership_role
        UNIQUE (membership_id, role),
    CONSTRAINT ck_membership_role
        CHECK (role IN ('ORGANIZATION_ADMIN', 'DOCTOR', 'RECEPTIONIST')),
    CONSTRAINT ck_membership_role_lifecycle
        CHECK (
            (active AND deactivated_at IS NULL)
            OR (NOT active AND deactivated_at IS NOT NULL)
        )
);

CREATE INDEX ix_membership_role_active
    ON organisation_membership_role (membership_id, role)
    WHERE active;

ALTER TABLE organisation_audit_event
    ADD COLUMN resource_type VARCHAR(40),
    ADD COLUMN resource_id UUID,
    ADD COLUMN target_user_id UUID;

UPDATE organisation_audit_event
SET resource_type = 'ORGANISATION',
    resource_id = organisation_id;

ALTER TABLE organisation_audit_event
    ALTER COLUMN resource_type SET NOT NULL,
    ALTER COLUMN resource_id SET NOT NULL,
    ADD CONSTRAINT ck_organisation_audit_resource_type
        CHECK (resource_type IN ('ORGANISATION', 'MEMBERSHIP'));

CREATE INDEX ix_organisation_audit_resource_occurred
    ON organisation_audit_event (
        resource_type,
        resource_id,
        occurred_at DESC
    );

CREATE INDEX ix_organisation_audit_target_occurred
    ON organisation_audit_event (target_user_id, occurred_at DESC)
    WHERE target_user_id IS NOT NULL;
