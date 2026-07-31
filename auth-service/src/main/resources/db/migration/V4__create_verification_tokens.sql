ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_lock_state
        CHECK (
            (status = 'LOCKED' AND locked_until IS NOT NULL)
            OR (status <> 'LOCKED' AND locked_until IS NULL)
        ),
    ADD CONSTRAINT ck_user_account_email_verified_at
        CHECK (
            email_verified_at IS NULL
            OR email_verified_at >= created_at
        ),
    ADD CONSTRAINT ck_user_account_last_login_at
        CHECK (
            last_login_at IS NULL
            OR last_login_at >= created_at
        ),
    ADD CONSTRAINT ck_user_account_password_changed_at
        CHECK (password_changed_at >= created_at);

CREATE TABLE verification_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_verification_token_user
        FOREIGN KEY (user_id)
        REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_verification_token_hash
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_verification_token_purpose
        CHECK (
            purpose IN (
                'EMAIL_VERIFICATION',
                'PASSWORD_RESET',
                'EMAIL_CHANGE'
            )
        ),
    CONSTRAINT ck_verification_token_expiration
        CHECK (expires_at > created_at),
    CONSTRAINT ck_verification_token_used_at
        CHECK (
            used_at IS NULL
            OR (used_at >= created_at AND used_at < expires_at)
        ),
    CONSTRAINT ck_verification_token_revocation
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
    CONSTRAINT ck_verification_token_terminal_state
        CHECK (used_at IS NULL OR revoked_at IS NULL),
    CONSTRAINT ck_verification_token_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_verification_token_hash
    ON verification_token (token_hash);

CREATE UNIQUE INDEX uq_verification_token_one_active_purpose
    ON verification_token (user_id, purpose)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE INDEX ix_verification_token_active_expiration
    ON verification_token (expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE INDEX ix_verification_token_user_history
    ON verification_token (user_id, purpose, created_at DESC);
