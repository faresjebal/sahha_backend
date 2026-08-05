ALTER TABLE user_session
    ADD COLUMN active_organisation_roles VARCHAR(256) NOT NULL DEFAULT '';

ALTER TABLE user_session
    ADD CONSTRAINT ck_user_session_active_organisation_context
        CHECK (
            (
                active_organisation_id IS NULL
                AND active_organisation_roles = ''
            )
            OR (
                active_organisation_id IS NOT NULL
                AND active_organisation_roles ~ '^[A-Z][A-Z0-9_]{1,63}(,[A-Z][A-Z0-9_]{1,63})*$'
            )
        );

ALTER TABLE security_event
    ADD COLUMN active_organisation_id UUID;

CREATE INDEX ix_security_event_active_organisation_occurred
    ON security_event (active_organisation_id, occurred_at DESC)
    WHERE active_organisation_id IS NOT NULL;
