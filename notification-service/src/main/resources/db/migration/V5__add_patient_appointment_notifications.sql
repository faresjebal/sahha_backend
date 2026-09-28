CREATE TABLE patient_appointment_notification (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL UNIQUE REFERENCES consumed_appointment_event(event_id),
    patient_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    appointment_id UUID NOT NULL,
    notification_type VARCHAR(64) NOT NULL CHECK (notification_type IN (
        'APPOINTMENT_REQUESTED', 'APPOINTMENT_CONFIRMED', 'APPOINTMENT_REJECTED',
        'APPOINTMENT_RESCHEDULED', 'APPOINTMENT_CANCELLED', 'PATIENT_CHECKED_IN',
        'APPOINTMENT_STARTED', 'APPOINTMENT_COMPLETED', 'APPOINTMENT_NO_SHOW')),
    appointment_status VARCHAR(32) NOT NULL CHECK (appointment_status IN (
        'REQUESTED', 'CONFIRMED', 'REJECTED', 'RESCHEDULED', 'CANCELLED',
        'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW')),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
    time_zone VARCHAR(64) NOT NULL,
    location_label VARCHAR(160) NOT NULL,
    resource_version BIGINT NOT NULL CHECK (resource_version >= 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ
);
CREATE INDEX ix_patient_notification_inbox
    ON patient_appointment_notification(patient_id, organisation_id, created_at DESC, id DESC);
CREATE INDEX ix_patient_notification_unread
    ON patient_appointment_notification(patient_id, organisation_id) WHERE read_at IS NULL;

CREATE FUNCTION protect_patient_notification_snapshot() RETURNS TRIGGER AS $$
BEGIN
    IF (to_jsonb(NEW) - 'read_at') IS DISTINCT FROM (to_jsonb(OLD) - 'read_at')
        OR (OLD.read_at IS NOT NULL AND NEW.read_at IS DISTINCT FROM OLD.read_at) THEN
        RAISE EXCEPTION 'Patient notification snapshot and acknowledged read time are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER patient_notification_immutable BEFORE UPDATE ON patient_appointment_notification
    FOR EACH ROW EXECUTE FUNCTION protect_patient_notification_snapshot();
