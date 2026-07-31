CREATE TABLE user_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_organisation_id UUID,
    device_id_hash VARCHAR(64) NOT NULL,
    device_name VARCHAR(120),
    user_agent VARCHAR(512),
    initial_ip VARCHAR(45),
    last_ip VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    idle_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by_user_id UUID,
    revocation_reason VARCHAR(255),
    credential_version_at_creation INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_user_session_user
        FOREIGN KEY (user_id)
        REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_user_session_revoked_by
        FOREIGN KEY (revoked_by_user_id)
        REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_user_session_id_user
        UNIQUE (id, user_id),
    CONSTRAINT ck_user_session_status
        CHECK (
            status IN (
                'ACTIVE',
                'REVOKED',
                'EXPIRED',
                'COMPROMISED'
            )
        ),
    CONSTRAINT ck_user_session_device_id_hash
        CHECK (device_id_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_user_session_device_name
        CHECK (device_name IS NULL OR btrim(device_name) <> ''),
    CONSTRAINT ck_user_session_user_agent
        CHECK (user_agent IS NULL OR btrim(user_agent) <> ''),
    CONSTRAINT ck_user_session_initial_ip
        CHECK (initial_ip IS NULL OR btrim(initial_ip) <> ''),
    CONSTRAINT ck_user_session_last_ip
        CHECK (last_ip IS NULL OR btrim(last_ip) <> ''),
    CONSTRAINT ck_user_session_expiration
        CHECK (
            last_activity_at >= created_at
            AND absolute_expires_at > created_at
            AND idle_expires_at > last_activity_at
            AND idle_expires_at <= absolute_expires_at
        ),
    CONSTRAINT ck_user_session_revocation
        CHECK (
            (
                status = 'ACTIVE'
                AND revoked_at IS NULL
                AND revoked_by_user_id IS NULL
                AND revocation_reason IS NULL
            )
            OR (
                status IN ('REVOKED', 'COMPROMISED')
                AND revoked_at IS NOT NULL
                AND revocation_reason IS NOT NULL
                AND btrim(revocation_reason) <> ''
            )
            OR (
                status = 'EXPIRED'
                AND revoked_at IS NULL
                AND revoked_by_user_id IS NULL
                AND revocation_reason IS NULL
            )
        ),
    CONSTRAINT ck_user_session_revoked_at
        CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT ck_user_session_credential_version
        CHECK (credential_version_at_creation >= 1),
    CONSTRAINT ck_user_session_version
        CHECK (version >= 0)
);

CREATE INDEX ix_user_session_active_user
    ON user_session (user_id, created_at DESC)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_user_session_active_organisation
    ON user_session (active_organisation_id)
    WHERE status = 'ACTIVE' AND active_organisation_id IS NOT NULL;

CREATE INDEX ix_user_session_device
    ON user_session (user_id, device_id_hash);

CREATE INDEX ix_user_session_idle_expiration
    ON user_session (idle_expires_at)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_user_session_absolute_expiration
    ON user_session (absolute_expires_at)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_user_session_revoked_by
    ON user_session (revoked_by_user_id)
    WHERE revoked_by_user_id IS NOT NULL;

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    parent_token_id UUID,
    replaced_by_token_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(255),
    created_ip VARCHAR(45),
    created_user_agent VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_refresh_token_identity_family
        UNIQUE (id, session_id, user_id),
    CONSTRAINT fk_refresh_token_session_user
        FOREIGN KEY (session_id, user_id)
        REFERENCES user_session (id, user_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_refresh_token_parent_family
        FOREIGN KEY (parent_token_id, session_id, user_id)
        REFERENCES refresh_token (id, session_id, user_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_refresh_token_replacement_family
        FOREIGN KEY (replaced_by_token_id, session_id, user_id)
        REFERENCES refresh_token (id, session_id, user_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_refresh_token_hash
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_refresh_token_expiration
        CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_token_used_at
        CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT ck_refresh_token_revocation
        CHECK (
            (
                revoked_at IS NULL
                AND revocation_reason IS NULL
            )
            OR (
                revoked_at IS NOT NULL
                AND revoked_at >= created_at
                AND revocation_reason IS NOT NULL
                AND btrim(revocation_reason) <> ''
            )
        ),
    CONSTRAINT ck_refresh_token_used_is_revoked
        CHECK (used_at IS NULL OR revoked_at IS NOT NULL),
    CONSTRAINT ck_refresh_token_parent_not_self
        CHECK (parent_token_id IS NULL OR parent_token_id <> id),
    CONSTRAINT ck_refresh_token_replacement_not_self
        CHECK (replaced_by_token_id IS NULL OR replaced_by_token_id <> id),
    CONSTRAINT ck_refresh_token_lineage_not_cycle
        CHECK (
            parent_token_id IS NULL
            OR replaced_by_token_id IS NULL
            OR parent_token_id <> replaced_by_token_id
        ),
    CONSTRAINT ck_refresh_token_replacement_state
        CHECK (
            replaced_by_token_id IS NULL
            OR (used_at IS NOT NULL AND revoked_at IS NOT NULL)
        ),
    CONSTRAINT ck_refresh_token_created_ip
        CHECK (created_ip IS NULL OR btrim(created_ip) <> ''),
    CONSTRAINT ck_refresh_token_created_user_agent
        CHECK (
            created_user_agent IS NULL
            OR btrim(created_user_agent) <> ''
        ),
    CONSTRAINT ck_refresh_token_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_refresh_token_hash
    ON refresh_token (token_hash);

CREATE UNIQUE INDEX uq_refresh_token_one_active_per_session
    ON refresh_token (session_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE UNIQUE INDEX uq_refresh_token_one_child_per_parent
    ON refresh_token (parent_token_id)
    WHERE parent_token_id IS NOT NULL;

CREATE UNIQUE INDEX uq_refresh_token_one_parent_per_replacement
    ON refresh_token (replaced_by_token_id)
    WHERE replaced_by_token_id IS NOT NULL;

CREATE INDEX ix_refresh_token_active_user
    ON refresh_token (user_id, session_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE INDEX ix_refresh_token_expiration
    ON refresh_token (expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;
