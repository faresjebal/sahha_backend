-- A distinct ownership domain: never retrofit a clinical file as a message upload.
CREATE TABLE message_attachment (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    message_request_id UUID NOT NULL,
    uploader_user_id UUID NOT NULL,
    upload_request_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(127) NOT NULL CHECK(content_type IN ('application/pdf','image/png','image/jpeg')),
    declared_size BIGINT NOT NULL CHECK(declared_size>0),
    expected_checksum VARCHAR(64) NOT NULL CHECK(expected_checksum ~ '^[0-9a-f]{64}$'),
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    upload_status VARCHAR(24) NOT NULL CHECK(upload_status IN ('NEGOTIATED','UPLOADING','STORED','FAILED')),
    scan_status VARCHAR(24) NOT NULL CHECK(scan_status IN ('PENDING','CLEAN','REJECTED')),
    token_digest VARCHAR(64) NOT NULL,
    token_expires_at TIMESTAMPTZ NOT NULL,
    token_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    uploaded_at TIMESTAMPTZ,
    UNIQUE(organisation_id,uploader_user_id,upload_request_id),
    UNIQUE(id,organisation_id),
    CHECK(scan_status='PENDING' OR upload_status='STORED'),
    CHECK(upload_status<>'STORED' OR uploaded_at IS NOT NULL)
);
CREATE TABLE message_attachment_download_grant (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    token_digest VARCHAR(64) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at>issued_at),
    used_at TIMESTAMPTZ,
    FOREIGN KEY(file_id,organisation_id) REFERENCES message_attachment(id,organisation_id)
);
CREATE TABLE message_attachment_audit (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    file_id UUID NOT NULL,
    action VARCHAR(48) NOT NULL,
    result VARCHAR(16) NOT NULL CHECK(result IN ('SUCCEEDED','DENIED','FAILED')),
    request_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_message_attachment_scope ON message_attachment(organisation_id,conversation_id,message_request_id);
CREATE INDEX ix_message_attachment_audit ON message_attachment_audit(organisation_id,file_id,occurred_at);
CREATE FUNCTION protect_message_attachment_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (to_jsonb(NEW)-ARRAY['upload_status','scan_status','token_digest','token_expires_at','token_used_at','uploaded_at'])
        IS DISTINCT FROM
       (to_jsonb(OLD)-ARRAY['upload_status','scan_status','token_digest','token_expires_at','token_used_at','uploaded_at']) THEN
        RAISE EXCEPTION 'message attachment identity is immutable';
    END IF;
    IF OLD.token_used_at IS NOT NULL AND
       (NEW.token_used_at IS DISTINCT FROM OLD.token_used_at OR NEW.token_digest<>OLD.token_digest
        OR NEW.token_expires_at<>OLD.token_expires_at) THEN
        RAISE EXCEPTION 'claimed upload ticket is immutable';
    END IF;
    IF OLD.uploaded_at IS NOT NULL AND NEW.uploaded_at IS DISTINCT FROM OLD.uploaded_at THEN
        RAISE EXCEPTION 'stored upload time is immutable';
    END IF;
    IF NEW.upload_status<>OLD.upload_status AND NOT
       ((OLD.upload_status='NEGOTIATED' AND NEW.upload_status='UPLOADING')
        OR (OLD.upload_status='UPLOADING' AND NEW.upload_status IN ('STORED','FAILED'))) THEN
        RAISE EXCEPTION 'invalid upload transition';
    END IF;
    IF NEW.scan_status<>OLD.scan_status AND NOT
       (OLD.scan_status='PENDING' AND NEW.scan_status IN ('CLEAN','REJECTED') AND NEW.upload_status='STORED') THEN
        RAISE EXCEPTION 'scan decision is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_message_attachment_identity BEFORE UPDATE ON message_attachment
    FOR EACH ROW EXECUTE FUNCTION protect_message_attachment_identity();
CREATE FUNCTION protect_message_attachment_grant() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (to_jsonb(NEW)-'used_at') IS DISTINCT FROM (to_jsonb(OLD)-'used_at')
       OR (OLD.used_at IS NOT NULL AND NEW.used_at IS DISTINCT FROM OLD.used_at) THEN
        RAISE EXCEPTION 'attachment grant and consumed time are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_message_attachment_grant BEFORE UPDATE ON message_attachment_download_grant
    FOR EACH ROW EXECUTE FUNCTION protect_message_attachment_grant();
CREATE FUNCTION reject_message_attachment_audit_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'attachment audit is append-only'; END;
$$;
CREATE TRIGGER trg_message_attachment_audit BEFORE UPDATE OR DELETE ON message_attachment_audit
    FOR EACH ROW EXECUTE FUNCTION reject_message_attachment_audit_mutation();
