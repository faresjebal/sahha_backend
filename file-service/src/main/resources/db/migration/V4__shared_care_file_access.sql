-- Preserve existing rows. Old short-lived tokens fail closed until replaced by a scoped token.
ALTER TABLE file_download_grant ADD COLUMN access_scope varchar(24) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE file_download_grant ADD CONSTRAINT ck_download_access_scope
    CHECK (access_scope IN ('LEGACY', 'OWN', 'SELECTED', 'SHARED_CARE'));
CREATE FUNCTION protect_file_download_grant_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.access_scope IS DISTINCT FROM OLD.access_scope THEN
        RAISE EXCEPTION 'download grant access scope is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_file_download_grant_scope_immutable BEFORE UPDATE ON file_download_grant
    FOR EACH ROW EXECUTE FUNCTION protect_file_download_grant_scope();

ALTER TABLE file_audit_event ALTER COLUMN medical_file_id DROP NOT NULL;
ALTER TABLE file_audit_event ADD CONSTRAINT ck_file_audit_resource_kind CHECK (
    (event_type = 'CARE_DOCUMENTS_READ' AND medical_file_id IS NULL
        AND consultation_id IS NOT NULL AND patient_registration_id IS NOT NULL)
    OR (event_type <> 'CARE_DOCUMENTS_READ' AND medical_file_id IS NOT NULL)
);
ALTER TABLE file_audit_event DROP CONSTRAINT ck_file_audit_event_type;
ALTER TABLE file_audit_event ADD CONSTRAINT ck_file_audit_event_type CHECK (event_type IN (
    'UPLOAD_NEGOTIATED', 'UPLOAD_STORED', 'UPLOAD_FAILED', 'SCAN_MARKED_CLEAN',
    'SCAN_REJECTED', 'DOWNLOAD_GRANT_ISSUED', 'DOWNLOADED', 'ACCESS_DENIED',
    'GRANT_EXPIRED', 'SHARED_METADATA_READ', 'CARE_DOCUMENTS_READ'
));
