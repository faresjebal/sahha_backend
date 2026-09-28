alter table communication_audit_event
    add column aggregate_type varchar(32),
    add column aggregate_id uuid,
    add column target_resource_type varchar(32),
    add column target_resource_id uuid;

update communication_audit_event
set aggregate_type = 'CONVERSATION',
    aggregate_id = conversation_id,
    target_resource_type = case when message_id is null then null else 'MESSAGE' end,
    target_resource_id = message_id;

alter table communication_audit_event
    alter column aggregate_type set not null,
    alter column aggregate_id set not null,
    alter column conversation_id drop not null;

create index ix_communication_audit_aggregate
    on communication_audit_event
        (organisation_id, aggregate_type, aggregate_id, occurred_at, id);

alter table communication_outbox_event
    add column destination_topic varchar(160)
        not null default 'sahha.communication.messages.v1';

alter table communication_outbox_event
    alter column destination_topic drop default;

create table referral_request (
    id uuid primary key,
    organisation_id uuid not null,
    request_id uuid not null,
    patient_registration_id uuid not null,
    sender_user_id uuid not null,
    sender_membership_id uuid not null,
    sender_display_name_snapshot varchar(201) not null,
    recipient_user_id uuid not null,
    recipient_membership_id uuid not null,
    recipient_display_name_snapshot varchar(201) not null,
    reason varchar(1000) not null,
    priority varchar(16) not null,
    clinical_summary varchar(4000),
    purpose varchar(500) not null,
    consent_type varchar(32) not null,
    consent_evidence_reference varchar(255) not null,
    consent_recorded_at timestamptz not null,
    access_expires_at timestamptz not null,
    submitted_immediately boolean not null,
    status varchar(16) not null,
    created_at timestamptz not null,
    sent_at timestamptz,
    accepted_at timestamptz,
    active_at timestamptz,
    rejected_at timestamptz,
    completed_at timestamptz,
    revoked_at timestamptz,
    expired_at timestamptz,
    last_action_by_user_id uuid not null,
    decision_reason varchar(500),
    version bigint not null default 0,
    constraint uq_referral_request unique
        (organisation_id, sender_user_id, request_id),
    constraint ck_referral_distinct_doctors check (sender_user_id <> recipient_user_id),
    constraint ck_referral_reason_nonblank check (length(btrim(reason)) between 4 and 1000),
    constraint ck_referral_priority check (priority in ('ROUTINE', 'URGENT')),
    constraint ck_referral_purpose_nonblank check (length(btrim(purpose)) between 4 and 500),
    constraint ck_referral_consent_type check (consent_type in
        ('EXPLICIT_DIGITAL', 'RECORDED_WRITTEN', 'RECORDED_VERBAL', 'LEGAL_BASIS', 'EMERGENCY')),
    constraint ck_referral_consent_reference_nonblank check
        (length(btrim(consent_evidence_reference)) between 3 and 255),
    constraint ck_referral_status check (status in
        ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'ACTIVE', 'COMPLETED', 'REVOKED', 'EXPIRED')),
    constraint ck_referral_access_window check (access_expires_at > created_at)
);

create index ix_referral_sender
    on referral_request (organisation_id, sender_user_id, sender_membership_id, created_at desc, id);

create index ix_referral_recipient
    on referral_request (organisation_id, recipient_user_id, recipient_membership_id, created_at desc, id);

create index ix_referral_expiry
    on referral_request (access_expires_at, id)
    where status in ('SENT', 'ACTIVE');

create table referral_share_item (
    id uuid primary key,
    referral_id uuid not null references referral_request(id),
    organisation_id uuid not null,
    resource_type varchar(32) not null,
    resource_id uuid not null,
    created_at timestamptz not null,
    constraint uq_referral_share_item unique (referral_id, resource_type, resource_id),
    constraint ck_referral_share_resource_type check (resource_type in
        ('CONSULTATION', 'DIAGNOSIS', 'MEDICATION', 'ALLERGY', 'MEDICAL_DOCUMENT'))
);

create index ix_referral_share_lookup
    on referral_share_item (organisation_id, resource_type, resource_id, referral_id);

create table referral_sharing_grant (
    id uuid primary key,
    referral_id uuid not null unique references referral_request(id),
    organisation_id uuid not null,
    patient_registration_id uuid not null,
    recipient_user_id uuid not null,
    recipient_membership_id uuid not null,
    status varchar(16) not null,
    valid_from timestamptz not null,
    valid_until timestamptz not null,
    revoked_at timestamptz,
    expired_at timestamptz,
    termination_reason varchar(500),
    version bigint not null default 0,
    constraint ck_referral_grant_status check (status in ('ACTIVE', 'REVOKED', 'EXPIRED')),
    constraint ck_referral_grant_window check (valid_until > valid_from)
);

create index ix_referral_grant_decision
    on referral_sharing_grant
        (organisation_id, recipient_user_id, recipient_membership_id,
         patient_registration_id, status, valid_until);

create trigger trg_referral_share_item_immutable
before update or delete on referral_share_item
for each row execute function reject_communication_immutable_mutation();
