ALTER TABLE organisation_membership
    ADD CONSTRAINT uq_membership_id_organisation
        UNIQUE (id, organisation_id);

ALTER TABLE department
    ADD CONSTRAINT uq_department_id_organisation
        UNIQUE (id, organisation_id);

CREATE TABLE staff_department_assignment (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    department_id UUID NOT NULL,
    position_title VARCHAR(120) NOT NULL,
    primary_assignment BOOLEAN NOT NULL,
    start_date DATE NOT NULL,
    planned_end_date DATE,
    status VARCHAR(24) NOT NULL,
    ended_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_staff_assignment_membership
        FOREIGN KEY (membership_id, organisation_id)
        REFERENCES organisation_membership (id, organisation_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_assignment_department
        FOREIGN KEY (department_id, organisation_id)
        REFERENCES department (id, organisation_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_staff_assignment_position
        CHECK (btrim(position_title) <> ''),
    CONSTRAINT ck_staff_assignment_dates
        CHECK (
            planned_end_date IS NULL
            OR planned_end_date >= start_date
        ),
    CONSTRAINT ck_staff_assignment_status
        CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_staff_assignment_lifecycle
        CHECK (
            (status = 'ACTIVE' AND ended_at IS NULL)
            OR (status = 'ENDED' AND ended_at IS NOT NULL)
        ),
    CONSTRAINT ck_staff_assignment_timestamps
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_staff_assignment_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_staff_assignment_active_department
    ON staff_department_assignment (membership_id, department_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_staff_assignment_active_primary
    ON staff_department_assignment (membership_id)
    WHERE status = 'ACTIVE' AND primary_assignment;

CREATE INDEX ix_staff_assignment_organisation_department
    ON staff_department_assignment (
        organisation_id,
        department_id,
        status,
        membership_id
    );

CREATE INDEX ix_staff_assignment_membership_created
    ON staff_department_assignment (membership_id, created_at DESC, id);

CREATE TABLE doctor_profile (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    specialty VARCHAR(120) NOT NULL,
    professional_title VARCHAR(120) NOT NULL,
    licence_number VARCHAR(80) NOT NULL,
    normalized_licence_number VARCHAR(80) NOT NULL,
    registration_authority VARCHAR(160) NOT NULL,
    biography VARCHAR(1000),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_doctor_profile_membership
        FOREIGN KEY (membership_id, organisation_id)
        REFERENCES organisation_membership (id, organisation_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_doctor_profile_membership
        UNIQUE (membership_id),
    CONSTRAINT uq_doctor_profile_organisation_licence
        UNIQUE (organisation_id, normalized_licence_number),
    CONSTRAINT ck_doctor_profile_required_fields
        CHECK (
            btrim(specialty) <> ''
            AND btrim(professional_title) <> ''
            AND btrim(licence_number) <> ''
            AND btrim(normalized_licence_number) <> ''
            AND normalized_licence_number = lower(btrim(normalized_licence_number))
            AND btrim(registration_authority) <> ''
        ),
    CONSTRAINT ck_doctor_profile_biography
        CHECK (biography IS NULL OR btrim(biography) <> ''),
    CONSTRAINT ck_doctor_profile_timestamps
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_doctor_profile_version
        CHECK (version >= 0)
);

CREATE INDEX ix_doctor_profile_organisation_specialty
    ON doctor_profile (organisation_id, specialty, membership_id);

ALTER TABLE organisation_audit_event
    DROP CONSTRAINT ck_organisation_audit_resource_type,
    ADD CONSTRAINT ck_organisation_audit_resource_type
        CHECK (
            resource_type IN (
                'ORGANISATION',
                'MEMBERSHIP',
                'DEPARTMENT',
                'STAFF_INVITATION',
                'DEPARTMENT_ASSIGNMENT',
                'DOCTOR_PROFILE'
            )
        );
