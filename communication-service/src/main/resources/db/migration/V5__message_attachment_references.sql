ALTER TABLE conversation_message ADD CONSTRAINT uq_message_id_org UNIQUE (id, organisation_id);
CREATE TABLE conversation_message_attachment (
    file_id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    organisation_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL CHECK (length(btrim(original_filename)) > 0),
    content_type VARCHAR(127) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    FOREIGN KEY (message_id, organisation_id) REFERENCES conversation_message(id, organisation_id)
);
CREATE INDEX ix_message_attachment_message ON conversation_message_attachment(organisation_id, message_id);
CREATE TRIGGER trg_message_attachment_immutable BEFORE UPDATE OR DELETE ON conversation_message_attachment
    FOR EACH ROW EXECUTE FUNCTION reject_communication_immutable_mutation();
