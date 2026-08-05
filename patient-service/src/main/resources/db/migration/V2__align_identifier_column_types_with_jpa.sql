ALTER TABLE patient_identity
    ALTER COLUMN identifier_fingerprint TYPE VARCHAR(64),
    ALTER COLUMN identifier_country_code TYPE VARCHAR(2);

ALTER TABLE patient_organisation_registration
    ALTER COLUMN country_code TYPE VARCHAR(2);
