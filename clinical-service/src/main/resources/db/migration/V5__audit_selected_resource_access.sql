ALTER TABLE clinical_access_audit_event
    DROP CONSTRAINT ck_clinical_access_resource_type;
ALTER TABLE clinical_access_audit_event
    ADD CONSTRAINT ck_clinical_access_resource_type CHECK (
        resource_type IN ('CONSULTATION', 'PATIENT_SUMMARY', 'DIAGNOSIS', 'MEDICATION', 'ALLERGY')
    );
