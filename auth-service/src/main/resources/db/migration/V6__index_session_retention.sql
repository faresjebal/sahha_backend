CREATE INDEX ix_user_session_retention
    ON user_session (absolute_expires_at, id);
