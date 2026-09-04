CREATE TABLE doctor_availability_schedule (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    doctor_user_id UUID NOT NULL,
    doctor_membership_id UUID NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    appointment_duration_minutes INTEGER NOT NULL,
    minimum_lead_time_minutes INTEGER NOT NULL,
    booking_horizon_days INTEGER NOT NULL,
    location_label VARCHAR(160) NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_availability_organisation_doctor
        UNIQUE (organisation_id, doctor_user_id),
    CONSTRAINT ck_availability_timezone CHECK (btrim(time_zone) <> ''),
    CONSTRAINT ck_availability_duration CHECK (
        appointment_duration_minutes BETWEEN 5 AND 240
        AND appointment_duration_minutes % 5 = 0
    ),
    CONSTRAINT ck_availability_lead CHECK (
        minimum_lead_time_minutes BETWEEN 0 AND 43200
    ),
    CONSTRAINT ck_availability_horizon CHECK (
        booking_horizon_days BETWEEN 1 AND 365
    ),
    CONSTRAINT ck_availability_location CHECK (btrim(location_label) <> ''),
    CONSTRAINT ck_availability_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_availability_version CHECK (version >= 0)
);

CREATE INDEX ix_availability_organisation_doctor
    ON doctor_availability_schedule (organisation_id, doctor_user_id, id);

CREATE TABLE doctor_weekly_availability (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL,
    day_of_week VARCHAR(9) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,

    CONSTRAINT fk_weekly_availability_schedule
        FOREIGN KEY (schedule_id)
        REFERENCES doctor_availability_schedule (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_weekly_availability_start
        UNIQUE (schedule_id, day_of_week, start_time),
    CONSTRAINT ck_weekly_availability_day CHECK (
        day_of_week IN (
            'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
            'FRIDAY', 'SATURDAY', 'SUNDAY'
        )
    ),
    CONSTRAINT ck_weekly_availability_time CHECK (end_time > start_time)
);

CREATE INDEX ix_weekly_availability_schedule_day
    ON doctor_weekly_availability (schedule_id, day_of_week, start_time);

CREATE TABLE doctor_availability_break (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL,
    day_of_week VARCHAR(9) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    label VARCHAR(80),

    CONSTRAINT fk_availability_break_schedule
        FOREIGN KEY (schedule_id)
        REFERENCES doctor_availability_schedule (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_availability_break_start
        UNIQUE (schedule_id, day_of_week, start_time),
    CONSTRAINT ck_availability_break_day CHECK (
        day_of_week IN (
            'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
            'FRIDAY', 'SATURDAY', 'SUNDAY'
        )
    ),
    CONSTRAINT ck_availability_break_time CHECK (end_time > start_time),
    CONSTRAINT ck_availability_break_label CHECK (
        label IS NULL OR btrim(label) <> ''
    )
);

CREATE INDEX ix_availability_break_schedule_day
    ON doctor_availability_break (schedule_id, day_of_week, start_time);

CREATE TABLE doctor_time_off (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL,
    time_off_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    reason VARCHAR(160) NOT NULL,

    CONSTRAINT fk_doctor_time_off_schedule
        FOREIGN KEY (schedule_id)
        REFERENCES doctor_availability_schedule (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_doctor_time_off_start
        UNIQUE (schedule_id, time_off_date, start_time),
    CONSTRAINT ck_doctor_time_off_period CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL
            AND end_time > start_time)
    ),
    CONSTRAINT ck_doctor_time_off_reason CHECK (btrim(reason) <> '')
);

CREATE INDEX ix_doctor_time_off_schedule_date
    ON doctor_time_off (schedule_id, time_off_date, start_time);
