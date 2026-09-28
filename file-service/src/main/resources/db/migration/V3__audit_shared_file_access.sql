ALTER TABLE file_audit_event ALTER COLUMN consultation_id DROP NOT NULL;
ALTER TABLE file_audit_event ALTER COLUMN patient_registration_id DROP NOT NULL;
ALTER TABLE file_audit_event ADD CONSTRAINT ck_file_audit_known_context CHECK (
    (consultation_id IS NOT NULL AND patient_registration_id IS NOT NULL)
    OR (event_type = 'ACCESS_DENIED' AND result = 'DENIED'
        AND consultation_id IS NULL AND patient_registration_id IS NULL)
);
ALTER TABLE file_audit_event DROP CONSTRAINT ck_file_audit_event_type;
ALTER TABLE file_audit_event ADD CONSTRAINT ck_file_audit_event_type CHECK (event_type IN (
    'UPLOAD_NEGOTIATED', 'UPLOAD_STORED', 'UPLOAD_FAILED',
    'SCAN_MARKED_CLEAN', 'SCAN_REJECTED', 'DOWNLOAD_GRANT_ISSUED',
    'DOWNLOADED', 'ACCESS_DENIED', 'GRANT_EXPIRED', 'SHARED_METADATA_READ'
));
