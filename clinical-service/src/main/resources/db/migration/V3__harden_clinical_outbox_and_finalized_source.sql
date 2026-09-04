ALTER TABLE clinical_outbox_event
    ADD COLUMN claim_token UUID,
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN claim_until TIMESTAMPTZ,
    ADD CONSTRAINT ck_clinical_outbox_claim CHECK (
        (claim_token IS NULL AND claimed_at IS NULL AND claim_until IS NULL)
        OR
        (published_at IS NULL AND claim_token IS NOT NULL
            AND claimed_at IS NOT NULL AND claim_until IS NOT NULL
            AND claim_until > claimed_at)
    );

CREATE INDEX ix_clinical_outbox_claim_expiry
    ON clinical_outbox_event (claim_until)
    WHERE published_at IS NULL AND claim_until IS NOT NULL;

CREATE OR REPLACE FUNCTION protect_finalized_consultation_source()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'FINALIZED' AND (
        NEW.id IS DISTINCT FROM OLD.id
        OR NEW.organisation_id IS DISTINCT FROM OLD.organisation_id
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
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
    ) THEN
        RAISE EXCEPTION 'finalized clinical source is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
