ALTER TABLE clinical_consultation
    ADD COLUMN clinical_assessment TEXT,
    ADD COLUMN treatment_plan TEXT,
    ADD COLUMN follow_up_instructions TEXT,
    ADD COLUMN finalized_at TIMESTAMPTZ,
    ADD COLUMN finalized_by_user_id UUID;

ALTER TABLE clinical_consultation
    DROP CONSTRAINT ck_clinical_consultation_status,
    ADD CONSTRAINT ck_clinical_consultation_status
        CHECK (status IN ('DRAFT', 'FINALIZED')),
    ADD CONSTRAINT ck_clinical_consultation_finalization CHECK (
        (status = 'DRAFT' AND finalized_at IS NULL
            AND finalized_by_user_id IS NULL)
        OR
        (status = 'FINALIZED' AND finalized_at IS NOT NULL
            AND finalized_by_user_id IS NOT NULL
            AND finalized_at >= created_at)
    );

CREATE TABLE clinical_symptom (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    position INTEGER NOT NULL,
    name VARCHAR(200) NOT NULL,
    onset_description VARCHAR(300),
    severity VARCHAR(24),
    notes VARCHAR(1000),
    CONSTRAINT fk_clinical_symptom_consultation
        FOREIGN KEY (consultation_id) REFERENCES clinical_consultation (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_clinical_symptom_position UNIQUE (consultation_id, position),
    CONSTRAINT ck_clinical_symptom_position CHECK (position >= 0),
    CONSTRAINT ck_clinical_symptom_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_clinical_symptom_severity CHECK (
        severity IS NULL OR severity IN ('MILD', 'MODERATE', 'SEVERE')
    )
);

CREATE TABLE clinical_history_entry (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    position INTEGER NOT NULL,
    category VARCHAR(24) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    notes VARCHAR(1000),
    CONSTRAINT fk_clinical_history_consultation
        FOREIGN KEY (consultation_id) REFERENCES clinical_consultation (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_clinical_history_position UNIQUE (consultation_id, position),
    CONSTRAINT ck_clinical_history_position CHECK (position >= 0),
    CONSTRAINT ck_clinical_history_category CHECK (
        category IN ('MEDICAL', 'SURGICAL', 'FAMILY', 'ALLERGY')
    ),
    CONSTRAINT ck_clinical_history_description CHECK (btrim(description) <> '')
);

CREATE TABLE clinical_vital_signs (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL UNIQUE,
    measured_at TIMESTAMPTZ NOT NULL,
    temperature_celsius NUMERIC(4,1),
    systolic_blood_pressure INTEGER,
    diastolic_blood_pressure INTEGER,
    heart_rate_bpm INTEGER,
    respiratory_rate_bpm INTEGER,
    oxygen_saturation_percent NUMERIC(5,2),
    weight_kg NUMERIC(6,2),
    height_cm NUMERIC(5,2),
    specialty_measurements JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT fk_clinical_vitals_consultation
        FOREIGN KEY (consultation_id) REFERENCES clinical_consultation (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_clinical_vitals_temperature CHECK (
        temperature_celsius IS NULL OR temperature_celsius BETWEEN 25 AND 50
    ),
    CONSTRAINT ck_clinical_vitals_pressure CHECK (
        (systolic_blood_pressure IS NULL
            OR systolic_blood_pressure BETWEEN 40 AND 300)
        AND (diastolic_blood_pressure IS NULL
            OR diastolic_blood_pressure BETWEEN 20 AND 200)
    ),
    CONSTRAINT ck_clinical_vitals_rates CHECK (
        (heart_rate_bpm IS NULL OR heart_rate_bpm BETWEEN 20 AND 300)
        AND (respiratory_rate_bpm IS NULL
            OR respiratory_rate_bpm BETWEEN 4 AND 100)
    ),
    CONSTRAINT ck_clinical_vitals_oxygen CHECK (
        oxygen_saturation_percent IS NULL
            OR oxygen_saturation_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_clinical_vitals_size CHECK (
        (weight_kg IS NULL OR weight_kg BETWEEN 0.10 AND 700)
        AND (height_cm IS NULL OR height_cm BETWEEN 10 AND 300)
    ),
    CONSTRAINT ck_clinical_vitals_specialty CHECK (
        jsonb_typeof(specialty_measurements) = 'object'
    )
);

CREATE TABLE clinical_examination_finding (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    position INTEGER NOT NULL,
    body_system VARCHAR(160) NOT NULL,
    finding VARCHAR(1000) NOT NULL,
    notes VARCHAR(1000),
    CONSTRAINT fk_clinical_exam_consultation
        FOREIGN KEY (consultation_id) REFERENCES clinical_consultation (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_clinical_exam_position UNIQUE (consultation_id, position),
    CONSTRAINT ck_clinical_exam_position CHECK (position >= 0),
    CONSTRAINT ck_clinical_exam_values CHECK (
        btrim(body_system) <> '' AND btrim(finding) <> ''
    )
);

CREATE TABLE clinical_diagnosis (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    position INTEGER NOT NULL,
    code VARCHAR(64),
    code_system VARCHAR(64),
    label VARCHAR(300) NOT NULL,
    diagnosis_type VARCHAR(24) NOT NULL,
    diagnosis_status VARCHAR(24) NOT NULL,
    notes VARCHAR(1000),
    CONSTRAINT fk_clinical_diagnosis_consultation
        FOREIGN KEY (consultation_id) REFERENCES clinical_consultation (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_clinical_diagnosis_position UNIQUE (consultation_id, position),
    CONSTRAINT ck_clinical_diagnosis_position CHECK (position >= 0),
    CONSTRAINT ck_clinical_diagnosis_code CHECK (
        (code IS NULL AND code_system IS NULL)
        OR (code IS NOT NULL AND btrim(code) <> ''
            AND code_system IS NOT NULL AND btrim(code_system) <> '')
    ),
    CONSTRAINT ck_clinical_diagnosis_label CHECK (btrim(label) <> ''),
    CONSTRAINT ck_clinical_diagnosis_type CHECK (
        diagnosis_type IN ('PRIMARY', 'SECONDARY', 'DIFFERENTIAL')
    ),
    CONSTRAINT ck_clinical_diagnosis_status CHECK (
        diagnosis_status IN ('CONFIRMED', 'SUSPECTED', 'RULED_OUT')
    )
);

CREATE TABLE clinical_medication_item (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    position INTEGER NOT NULL,
    medication_kind VARCHAR(24) NOT NULL,
    medication_name VARCHAR(300) NOT NULL,
    strength VARCHAR(100),
    medication_form VARCHAR(100),
    dosage VARCHAR(160),
    frequency VARCHAR(160),
    route VARCHAR(100),
    duration VARCHAR(160),
    quantity VARCHAR(100),
    special_instructions VARCHAR(1000),
    CONSTRAINT fk_clinical_medication_consultation
        FOREIGN KEY (consultation_id) REFERENCES clinical_consultation (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_clinical_medication_position UNIQUE (consultation_id, position),
    CONSTRAINT ck_clinical_medication_position CHECK (position >= 0),
    CONSTRAINT ck_clinical_medication_kind CHECK (
        medication_kind IN ('CURRENT', 'PRESCRIBED')
    ),
    CONSTRAINT ck_clinical_medication_name CHECK (btrim(medication_name) <> '')
);

CREATE TABLE clinical_correction (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id UUID,
    field_name VARCHAR(64) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    reason VARCHAR(1000) NOT NULL,
    consultation_version BIGINT NOT NULL,
    corrected_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_clinical_correction_consultation
        FOREIGN KEY (consultation_id) REFERENCES clinical_consultation (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_clinical_correction_target CHECK (
        (target_type = 'CONSULTATION' AND target_id IS NULL)
        OR
        (target_type IN ('SYMPTOM', 'HISTORY', 'VITAL_SIGNS',
            'EXAMINATION', 'DIAGNOSIS', 'MEDICATION') AND target_id IS NOT NULL)
    ),
    CONSTRAINT ck_clinical_correction_field CHECK (btrim(field_name) <> ''),
    CONSTRAINT ck_clinical_correction_changed CHECK (
        old_value IS DISTINCT FROM new_value
    ),
    CONSTRAINT ck_clinical_correction_reason CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_clinical_correction_version CHECK (consultation_version > 0)
);

CREATE INDEX ix_clinical_correction_effective
    ON clinical_correction (
        consultation_id, target_type, target_id, field_name,
        consultation_version DESC, corrected_at DESC, id
    );

CREATE OR REPLACE FUNCTION reject_clinical_correction_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'clinical_correction rows are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER clinical_correction_append_only
BEFORE UPDATE OR DELETE ON clinical_correction
FOR EACH ROW EXECUTE FUNCTION reject_clinical_correction_mutation();

CREATE OR REPLACE FUNCTION protect_finalized_consultation_source()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'FINALIZED' AND (
        NEW.organisation_id IS DISTINCT FROM OLD.organisation_id
        OR NEW.appointment_id IS DISTINCT FROM OLD.appointment_id
        OR NEW.patient_registration_id IS DISTINCT FROM OLD.patient_registration_id
        OR NEW.patient_id IS DISTINCT FROM OLD.patient_id
        OR NEW.doctor_user_id IS DISTINCT FROM OLD.doctor_user_id
        OR NEW.doctor_membership_id IS DISTINCT FROM OLD.doctor_membership_id
        OR NEW.status IS DISTINCT FROM OLD.status
        OR NEW.reason_for_consultation IS DISTINCT FROM OLD.reason_for_consultation
        OR NEW.draft_notes IS DISTINCT FROM OLD.draft_notes
        OR NEW.clinical_assessment IS DISTINCT FROM OLD.clinical_assessment
        OR NEW.treatment_plan IS DISTINCT FROM OLD.treatment_plan
        OR NEW.follow_up_instructions IS DISTINCT FROM OLD.follow_up_instructions
        OR NEW.finalized_at IS DISTINCT FROM OLD.finalized_at
        OR NEW.finalized_by_user_id IS DISTINCT FROM OLD.finalized_by_user_id
    ) THEN
        RAISE EXCEPTION 'finalized clinical source is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER clinical_consultation_finalized_source_immutable
BEFORE UPDATE ON clinical_consultation
FOR EACH ROW EXECUTE FUNCTION protect_finalized_consultation_source();

CREATE OR REPLACE FUNCTION protect_finalized_clinical_child()
RETURNS TRIGGER AS $$
DECLARE
    owning_consultation UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        owning_consultation := OLD.consultation_id;
    ELSE
        owning_consultation := NEW.consultation_id;
    END IF;
    IF EXISTS (
        SELECT 1 FROM clinical_consultation
        WHERE id = owning_consultation AND status = 'FINALIZED'
    ) THEN
        RAISE EXCEPTION 'finalized clinical child source is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER clinical_symptom_finalized_immutable
BEFORE INSERT OR UPDATE OR DELETE ON clinical_symptom
FOR EACH ROW EXECUTE FUNCTION protect_finalized_clinical_child();
CREATE TRIGGER clinical_history_finalized_immutable
BEFORE INSERT OR UPDATE OR DELETE ON clinical_history_entry
FOR EACH ROW EXECUTE FUNCTION protect_finalized_clinical_child();
CREATE TRIGGER clinical_vitals_finalized_immutable
BEFORE INSERT OR UPDATE OR DELETE ON clinical_vital_signs
FOR EACH ROW EXECUTE FUNCTION protect_finalized_clinical_child();
CREATE TRIGGER clinical_exam_finalized_immutable
BEFORE INSERT OR UPDATE OR DELETE ON clinical_examination_finding
FOR EACH ROW EXECUTE FUNCTION protect_finalized_clinical_child();
CREATE TRIGGER clinical_diagnosis_finalized_immutable
BEFORE INSERT OR UPDATE OR DELETE ON clinical_diagnosis
FOR EACH ROW EXECUTE FUNCTION protect_finalized_clinical_child();
CREATE TRIGGER clinical_medication_finalized_immutable
BEFORE INSERT OR UPDATE OR DELETE ON clinical_medication_item
FOR EACH ROW EXECUTE FUNCTION protect_finalized_clinical_child();

ALTER TABLE clinical_audit_event
    DROP CONSTRAINT ck_clinical_audit_event_type,
    ADD CONSTRAINT ck_clinical_audit_event_type CHECK (
        event_type IN (
            'CONSULTATION_DRAFT_CREATED',
            'CONSULTATION_DRAFT_UPDATED',
            'CONSULTATION_FINALIZED',
            'CONSULTATION_CORRECTED'
        )
    );
