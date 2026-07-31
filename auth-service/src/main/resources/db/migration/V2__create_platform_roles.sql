CREATE TABLE platform_role (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_platform_role_code
        CHECK (
            code = upper(btrim(code))
            AND code ~ '^[A-Z][A-Z0-9_]*$'
        ),
    CONSTRAINT ck_platform_role_description_not_blank
        CHECK (btrim(description) <> ''),
    CONSTRAINT ck_platform_role_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_platform_role_code
    ON platform_role (code);

INSERT INTO platform_role (
    id,
    code,
    description
) VALUES (
    'a0000000-0000-4000-8000-000000000001',
    'PLATFORM_ADMIN',
    'Manages the Sahha platform without automatic access to patient clinical records.'
);

CREATE TABLE user_platform_role (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_by_user_id UUID,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_user_platform_role_user
        FOREIGN KEY (user_id)
        REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_user_platform_role_role
        FOREIGN KEY (role_id)
        REFERENCES platform_role (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_user_platform_role_assigned_by
        FOREIGN KEY (assigned_by_user_id)
        REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_user_platform_role_user_role
        UNIQUE (user_id, role_id),
    CONSTRAINT ck_user_platform_role_deactivation
        CHECK (
            (active = TRUE AND deactivated_at IS NULL)
            OR (active = FALSE AND deactivated_at IS NOT NULL)
        ),
    CONSTRAINT ck_user_platform_role_version
        CHECK (version >= 0)
);

CREATE INDEX ix_user_platform_role_active_user
    ON user_platform_role (user_id)
    WHERE active = TRUE;

CREATE INDEX ix_user_platform_role_active_role
    ON user_platform_role (role_id)
    WHERE active = TRUE;

CREATE INDEX ix_user_platform_role_assigned_by
    ON user_platform_role (assigned_by_user_id)
    WHERE assigned_by_user_id IS NOT NULL;
