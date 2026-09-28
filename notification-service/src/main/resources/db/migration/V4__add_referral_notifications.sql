ALTER TABLE in_app_notification
    DROP CONSTRAINT ck_in_app_notification_type,
    DROP CONSTRAINT ck_in_app_notification_resource,
    DROP CONSTRAINT ck_in_app_notification_shape;

ALTER TABLE in_app_notification
    ADD CONSTRAINT ck_in_app_notification_type CHECK (notification_type IN (
        'APPOINTMENT_REQUESTED', 'APPOINTMENT_RESCHEDULED', 'APPOINTMENT_CANCELLED',
        'PATIENT_CHECKED_IN', 'MESSAGE_RECEIVED', 'REFERRAL_RECEIVED',
        'REFERRAL_ACCEPTED', 'REFERRAL_REJECTED', 'REFERRAL_REVOKED',
        'REFERRAL_COMPLETED', 'REFERRAL_EXPIRED')),
    ADD CONSTRAINT ck_in_app_notification_resource CHECK (
        resource_type IN ('APPOINTMENT', 'CONVERSATION', 'REFERRAL')),
    ADD CONSTRAINT ck_in_app_notification_shape CHECK (
        (resource_type = 'APPOINTMENT'
            AND notification_type IN ('APPOINTMENT_REQUESTED', 'APPOINTMENT_RESCHEDULED',
                'APPOINTMENT_CANCELLED', 'PATIENT_CHECKED_IN')
            AND appointment_status IS NOT NULL
            AND appointment_starts_at IS NOT NULL
            AND appointment_ends_at IS NOT NULL
            AND appointment_ends_at > appointment_starts_at
            AND appointment_time_zone IS NOT NULL AND btrim(appointment_time_zone) <> ''
            AND appointment_location_label IS NOT NULL AND btrim(appointment_location_label) <> '')
        OR
        (((resource_type = 'CONVERSATION' AND notification_type = 'MESSAGE_RECEIVED')
            OR (resource_type = 'REFERRAL' AND notification_type IN (
                'REFERRAL_RECEIVED', 'REFERRAL_ACCEPTED', 'REFERRAL_REJECTED',
                'REFERRAL_REVOKED', 'REFERRAL_COMPLETED', 'REFERRAL_EXPIRED')))
            AND appointment_status IS NULL AND appointment_starts_at IS NULL
            AND appointment_ends_at IS NULL AND appointment_time_zone IS NULL
            AND appointment_location_label IS NULL)
    );

CREATE TABLE consumed_referral_event (
    event_id UUID PRIMARY KEY,
    source_topic VARCHAR(249) NOT NULL CHECK (btrim(source_topic) <> ''),
    source_partition INTEGER NOT NULL CHECK (source_partition >= 0),
    source_offset BIGINT NOT NULL CHECK (source_offset >= 0),
    event_type VARCHAR(48) NOT NULL CHECK (event_type IN (
        'referral.sent.v1', 'referral.accepted.v1', 'referral.rejected.v1',
        'referral.revoked.v1', 'referral.completed.v1', 'referral.expired.v1')),
    organisation_id UUID NOT NULL,
    referral_id UUID NOT NULL,
    resource_version BIGINT NOT NULL CHECK (resource_version >= 0),
    event_occurred_at TIMESTAMPTZ NOT NULL,
    recipient_count INTEGER NOT NULL CHECK (recipient_count BETWEEN 1 AND 2),
    outcome VARCHAR(32) NOT NULL CHECK (outcome IN ('PROCESSING', 'NOTIFICATION_CREATED', 'STALE')),
    processed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (source_topic, source_partition, source_offset),
    CHECK ((event_type = 'referral.expired.v1' AND recipient_count = 2)
        OR (event_type <> 'referral.expired.v1' AND recipient_count = 1))
);

CREATE TABLE referral_notification_cursor (
    organisation_id UUID NOT NULL,
    referral_id UUID NOT NULL,
    last_resource_version BIGINT NOT NULL CHECK (last_resource_version >= 0),
    PRIMARY KEY (organisation_id, referral_id)
);

CREATE FUNCTION protect_consumed_referral_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'consumed referral evidence cannot be deleted';
    END IF;
    IF OLD.outcome <> 'PROCESSING'
        OR NEW.outcome NOT IN ('NOTIFICATION_CREATED', 'STALE')
        OR (to_jsonb(NEW) - 'outcome') IS DISTINCT FROM (to_jsonb(OLD) - 'outcome') THEN
        RAISE EXCEPTION 'consumed referral evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_consumed_referral_event
    BEFORE UPDATE OR DELETE ON consumed_referral_event
    FOR EACH ROW EXECUTE FUNCTION protect_consumed_referral_event();
