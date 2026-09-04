create table medical_file (
    id uuid primary key,
    organisation_id uuid not null,
    consultation_id uuid not null,
    patient_registration_id uuid not null,
    patient_id uuid not null,
    uploader_user_id uuid not null,
    original_filename varchar(255) not null,
    content_type varchar(127) not null,
    declared_size bigint not null,
    actual_size bigint,
    expected_checksum_sha256 char(64),
    checksum_sha256 char(64),
    storage_key varchar(512) not null unique,
    access_category varchar(32) not null,
    upload_status varchar(24) not null,
    scan_status varchar(24) not null,
    upload_ticket_digest char(64) not null unique,
    upload_ticket_expires_at timestamptz not null,
    upload_ticket_used_at timestamptz,
    upload_failure_code varchar(64),
    created_at timestamptz not null,
    uploaded_at timestamptz,
    available_at timestamptz,
    rejected_at timestamptz,
    version bigint not null default 0,
    constraint ck_medical_file_name check (length(trim(original_filename)) between 1 and 255),
    constraint ck_medical_file_type check (length(trim(content_type)) between 1 and 127),
    constraint ck_medical_file_declared_size check (declared_size > 0),
    constraint ck_medical_file_actual_size check (actual_size is null or actual_size > 0),
    constraint ck_medical_file_expected_checksum check (
        expected_checksum_sha256 is null
        or expected_checksum_sha256 ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_medical_file_checksum check (
        checksum_sha256 is null or checksum_sha256 ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_medical_file_access_category check (
        access_category in ('CONSULTATION_DOCUMENT')
    ),
    constraint ck_medical_file_upload_status check (
        upload_status in ('NEGOTIATED', 'STORED', 'FAILED')
    ),
    constraint ck_medical_file_scan_status check (
        scan_status in ('PENDING', 'CLEAN', 'REJECTED')
    ),
    constraint ck_medical_file_storage_state check (
        (upload_status = 'NEGOTIATED'
            and actual_size is null
            and checksum_sha256 is null
            and uploaded_at is null)
        or (upload_status = 'STORED'
            and actual_size is not null
            and checksum_sha256 is not null
            and uploaded_at is not null)
        or (upload_status = 'FAILED')
    ),
    constraint ck_medical_file_availability check (
        (scan_status = 'PENDING' and available_at is null and rejected_at is null)
        or (scan_status = 'CLEAN' and upload_status = 'STORED'
            and available_at is not null and rejected_at is null)
        or (scan_status = 'REJECTED' and available_at is null
            and rejected_at is not null)
    )
);

create index ix_medical_file_consultation
    on medical_file (organisation_id, consultation_id, created_at desc);
create index ix_medical_file_patient
    on medical_file (organisation_id, patient_registration_id, created_at desc);
create index ix_medical_file_scan_queue
    on medical_file (scan_status, uploaded_at)
    where upload_status = 'STORED' and scan_status = 'PENDING';

create table file_download_grant (
    id uuid primary key,
    medical_file_id uuid not null references medical_file(id),
    organisation_id uuid not null,
    actor_user_id uuid not null,
    token_digest char(64) not null unique,
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    request_id varchar(128) not null,
    version bigint not null default 0,
    constraint ck_file_download_grant_expiry check (expires_at > issued_at),
    constraint ck_file_download_grant_use check (used_at is null or used_at >= issued_at)
);

create index ix_file_download_grant_expiry
    on file_download_grant (expires_at)
    where used_at is null;

create table file_audit_event (
    id uuid primary key,
    organisation_id uuid not null,
    actor_user_id uuid not null,
    medical_file_id uuid not null,
    consultation_id uuid not null,
    patient_registration_id uuid not null,
    event_type varchar(48) not null,
    result varchar(16) not null,
    reason_code varchar(64),
    request_id varchar(128) not null,
    occurred_at timestamptz not null,
    constraint ck_file_audit_event_type check (event_type in (
        'UPLOAD_NEGOTIATED', 'UPLOAD_STORED', 'UPLOAD_FAILED',
        'SCAN_MARKED_CLEAN', 'SCAN_REJECTED', 'DOWNLOAD_GRANT_ISSUED',
        'DOWNLOADED', 'ACCESS_DENIED', 'GRANT_EXPIRED'
    )),
    constraint ck_file_audit_result check (result in ('SUCCEEDED', 'DENIED', 'FAILED'))
);

create index ix_file_audit_resource
    on file_audit_event (medical_file_id, occurred_at desc);
create index ix_file_audit_actor
    on file_audit_event (organisation_id, actor_user_id, occurred_at desc);

create table file_outbox_event (
    id uuid primary key,
    file_audit_event_id uuid not null unique references file_audit_event(id),
    organisation_id uuid not null,
    medical_file_id uuid not null,
    event_type varchar(64) not null,
    aggregate_version bigint not null,
    payload jsonb not null,
    occurred_at timestamptz not null,
    published_at timestamptz,
    publication_attempts integer not null default 0,
    last_attempt_at timestamptz,
    next_attempt_at timestamptz not null,
    last_error_code varchar(128),
    claim_token uuid,
    claimed_at timestamptz,
    claim_until timestamptz,
    version bigint not null default 0,
    constraint ck_file_outbox_event_type check (
        event_type in ('medical-file.available.v1', 'medical-file.rejected.v1')
    ),
    constraint ck_file_outbox_payload_object check (jsonb_typeof(payload) = 'object'),
    constraint ck_file_outbox_attempts check (publication_attempts >= 0),
    constraint ck_file_outbox_claim check (
        (claim_token is null and claimed_at is null and claim_until is null)
        or (claim_token is not null and claimed_at is not null
            and claim_until is not null and claim_until > claimed_at)
    )
);

create index ix_file_outbox_pending
    on file_outbox_event (next_attempt_at, occurred_at)
    where published_at is null;

create or replace function reject_file_audit_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'file audit events are append-only';
end;
$$;

create trigger trg_file_audit_append_only
before update or delete on file_audit_event
for each row execute function reject_file_audit_mutation();

create or replace function protect_medical_file_identity()
returns trigger
language plpgsql
as $$
begin
    if new.id is distinct from old.id
        or new.organisation_id is distinct from old.organisation_id
        or new.consultation_id is distinct from old.consultation_id
        or new.patient_registration_id is distinct from old.patient_registration_id
        or new.patient_id is distinct from old.patient_id
        or new.uploader_user_id is distinct from old.uploader_user_id
        or new.storage_key is distinct from old.storage_key
        or new.created_at is distinct from old.created_at then
        raise exception 'medical file ownership is immutable';
    end if;
    return new;
end;
$$;

create trigger trg_medical_file_identity_immutable
before update on medical_file
for each row execute function protect_medical_file_identity();

create or replace function protect_file_download_grant_identity()
returns trigger
language plpgsql
as $$
begin
    if new.id is distinct from old.id
        or new.medical_file_id is distinct from old.medical_file_id
        or new.organisation_id is distinct from old.organisation_id
        or new.actor_user_id is distinct from old.actor_user_id
        or new.token_digest is distinct from old.token_digest
        or new.issued_at is distinct from old.issued_at
        or new.expires_at is distinct from old.expires_at
        or new.request_id is distinct from old.request_id then
        raise exception 'file download grant identity is immutable';
    end if;
    return new;
end;
$$;

create trigger trg_file_download_grant_identity_immutable
before update on file_download_grant
for each row execute function protect_file_download_grant_identity();
