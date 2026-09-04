alter table medical_file
    alter column expected_checksum_sha256 type varchar(64),
    alter column checksum_sha256 type varchar(64),
    alter column upload_ticket_digest type varchar(64);

alter table file_download_grant
    alter column token_digest type varchar(64);
