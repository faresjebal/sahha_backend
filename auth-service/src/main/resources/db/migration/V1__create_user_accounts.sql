CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    credential_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_user_account_email_not_blank
        CHECK (btrim(email) <> ''),
    CONSTRAINT ck_user_account_normalized_email
        CHECK (
            btrim(normalized_email) <> ''
            AND normalized_email = lower(btrim(normalized_email))
        ),
    CONSTRAINT ck_user_account_password_hash_not_blank
        CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_user_account_first_name_not_blank
        CHECK (btrim(first_name) <> ''),
    CONSTRAINT ck_user_account_last_name_not_blank
        CHECK (btrim(last_name) <> ''),
    CONSTRAINT ck_user_account_status
        CHECK (
            status IN (
                'PENDING_VERIFICATION',
                'ACTIVE',
                'LOCKED',
                'SUSPENDED',
                'DISABLED'
            )
        ),
    CONSTRAINT ck_user_account_failed_login_attempts
        CHECK (failed_login_attempts >= 0),
    CONSTRAINT ck_user_account_credential_version
        CHECK (credential_version >= 1),
    CONSTRAINT ck_user_account_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_user_account_normalized_email
    ON user_account (normalized_email);

CREATE INDEX ix_user_account_status
    ON user_account (status);

CREATE INDEX ix_user_account_locked_until
    ON user_account (locked_until)
    WHERE locked_until IS NOT NULL;
