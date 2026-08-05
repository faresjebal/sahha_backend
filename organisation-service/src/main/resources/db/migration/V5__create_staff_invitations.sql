CREATE TABLE staff_invitation (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    role VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by_user_id UUID,
    accepted_membership_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_staff_invitation_organisation
        FOREIGN KEY (organisation_id)
        REFERENCES organisation (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_invitation_membership
        FOREIGN KEY (accepted_membership_id)
        REFERENCES organisation_membership (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_staff_invitation_email
        CHECK (
            btrim(email) <> ''
            AND btrim(normalized_email) <> ''
            AND normalized_email = lower(btrim(normalized_email))
        ),
    CONSTRAINT ck_staff_invitation_role
        CHECK (role IN ('DOCTOR', 'RECEPTIONIST')),
    CONSTRAINT ck_staff_invitation_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'REVOKED')),
    CONSTRAINT ck_staff_invitation_lifecycle
        CHECK (
            (status = 'PENDING'
                AND resolved_at IS NULL
                AND resolved_by_user_id IS NULL
                AND accepted_membership_id IS NULL)
            OR (status = 'ACCEPTED'
                AND resolved_at IS NOT NULL
                AND resolved_by_user_id IS NOT NULL
                AND accepted_membership_id IS NOT NULL)
            OR (status IN ('REJECTED', 'REVOKED')
                AND resolved_at IS NOT NULL
                AND resolved_by_user_id IS NOT NULL
                AND accepted_membership_id IS NULL)
        ),
    CONSTRAINT ck_staff_invitation_timestamps
        CHECK (
            expires_at > created_at
            AND updated_at >= created_at
            AND (resolved_at IS NULL OR resolved_at >= created_at)
        ),
    CONSTRAINT ck_staff_invitation_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_staff_invitation_pending_target
    ON staff_invitation (organisation_id, normalized_email)
    WHERE status = 'PENDING';

CREATE INDEX ix_staff_invitation_organisation_status_created
    ON staff_invitation (organisation_id, status, created_at DESC, id);

CREATE INDEX ix_staff_invitation_target_created
    ON staff_invitation (normalized_email, created_at DESC, id);

ALTER TABLE organisation_audit_event
    DROP CONSTRAINT ck_organisation_audit_resource_type,
    ADD CONSTRAINT ck_organisation_audit_resource_type
        CHECK (
            resource_type IN (
                'ORGANISATION',
                'MEMBERSHIP',
                'DEPARTMENT',
                'STAFF_INVITATION'
            )
        );
