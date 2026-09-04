CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE scheduled_appointment (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    booking_request_id UUID NOT NULL,
    patient_registration_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    doctor_user_id UUID NOT NULL,
    doctor_membership_id UUID NOT NULL,
    availability_schedule_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    location_label VARCHAR(160) NOT NULL,
    booked_by_user_id UUID NOT NULL,
    booked_by_membership_id UUID NOT NULL,
    booked_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_appointment_availability_schedule
        FOREIGN KEY (availability_schedule_id)
        REFERENCES doctor_availability_schedule (id),
    CONSTRAINT uq_appointment_booking_request
        UNIQUE (organisation_id, booking_request_id),
    CONSTRAINT ck_appointment_status CHECK (
        status IN (
            'REQUESTED', 'CONFIRMED', 'RESCHEDULED', 'CANCELLED',
            'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW',
            'REJECTED'
        )
    ),
    CONSTRAINT ck_appointment_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_appointment_timezone CHECK (btrim(time_zone) <> ''),
    CONSTRAINT ck_appointment_location CHECK (btrim(location_label) <> ''),
    CONSTRAINT ck_appointment_timestamps CHECK (
        booked_at = created_at AND updated_at >= created_at
    ),
    CONSTRAINT ck_appointment_version CHECK (version >= 0)
);

ALTER TABLE scheduled_appointment
    ADD CONSTRAINT ex_appointment_doctor_time
    EXCLUDE USING gist (
        organisation_id WITH =,
        doctor_user_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
    WHERE (status NOT IN ('CANCELLED', 'REJECTED'));

CREATE INDEX ix_appointment_organisation_patient
    ON scheduled_appointment (
        organisation_id,
        patient_registration_id,
        starts_at DESC
    );

CREATE INDEX ix_appointment_organisation_doctor
    ON scheduled_appointment (
        organisation_id,
        doctor_user_id,
        starts_at
    );

CREATE TABLE appointment_audit_event (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    actor_user_id UUID NOT NULL,
    actor_membership_id UUID NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_appointment_audit_appointment
        FOREIGN KEY (appointment_id)
        REFERENCES scheduled_appointment (id),
    CONSTRAINT ck_appointment_audit_event_type CHECK (
        event_type IN ('APPOINTMENT_BOOKED')
    ),
    CONSTRAINT ck_appointment_audit_new_status CHECK (
        new_status IN (
            'REQUESTED', 'CONFIRMED', 'RESCHEDULED', 'CANCELLED',
            'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW',
            'REJECTED'
        )
    ),
    CONSTRAINT ck_appointment_audit_previous_status CHECK (
        previous_status IS NULL OR previous_status IN (
            'REQUESTED', 'CONFIRMED', 'RESCHEDULED', 'CANCELLED',
            'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW',
            'REJECTED'
        )
    ),
    CONSTRAINT ck_appointment_audit_request_id CHECK (
        btrim(request_id) <> ''
    )
);

CREATE INDEX ix_appointment_audit_appointment_time
    ON appointment_audit_event (appointment_id, occurred_at, id);
