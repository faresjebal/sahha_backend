create table conversation_thread (
    id uuid primary key,
    organisation_id uuid not null,
    creation_request_id uuid not null,
    subject varchar(160) not null,
    patient_registration_id uuid,
    created_by_user_id uuid not null,
    created_by_membership_id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    last_message_at timestamptz not null,
    version bigint not null default 0,
    constraint uq_conversation_creation unique
        (organisation_id, created_by_user_id, creation_request_id),
    constraint ck_conversation_subject_nonblank check (length(btrim(subject)) between 4 and 160)
);

create index ix_conversation_org_last_message
    on conversation_thread (organisation_id, last_message_at desc, id);

create table conversation_participant (
    id uuid primary key,
    conversation_id uuid not null references conversation_thread(id),
    organisation_id uuid not null,
    user_id uuid not null,
    membership_id uuid not null,
    display_name_snapshot varchar(201) not null,
    joined_at timestamptz not null,
    last_read_at timestamptz,
    active boolean not null default true,
    version bigint not null default 0,
    constraint uq_conversation_participant unique (conversation_id, user_id),
    constraint ck_participant_display_name_nonblank check (length(btrim(display_name_snapshot)) > 0)
);

create index ix_participant_user_org
    on conversation_participant (user_id, organisation_id, active, conversation_id);

create table conversation_message (
    id uuid primary key,
    conversation_id uuid not null references conversation_thread(id),
    organisation_id uuid not null,
    message_request_id uuid not null,
    sender_user_id uuid not null,
    sender_membership_id uuid not null,
    sender_display_name_snapshot varchar(201) not null,
    body varchar(4000) not null,
    sent_at timestamptz not null,
    constraint uq_message_request unique (conversation_id, sender_user_id, message_request_id),
    constraint ck_message_body_nonblank check (length(btrim(body)) between 1 and 4000)
);

create index ix_message_conversation_sent
    on conversation_message (conversation_id, sent_at desc, id desc);

create table communication_audit_event (
    id uuid primary key,
    organisation_id uuid not null,
    conversation_id uuid not null,
    message_id uuid,
    actor_user_id uuid not null,
    event_type varchar(48) not null,
    resource_version bigint not null,
    occurred_at timestamptz not null
);

create index ix_communication_audit_conversation
    on communication_audit_event (organisation_id, conversation_id, occurred_at, id);

create table communication_outbox_event (
    id uuid primary key,
    audit_event_id uuid not null unique references communication_audit_event(id),
    organisation_id uuid not null,
    aggregate_id uuid not null,
    event_type varchar(64) not null,
    payload jsonb not null,
    occurred_at timestamptz not null,
    published_at timestamptz,
    publication_attempts integer not null default 0,
    last_attempt_at timestamptz,
    last_error_code varchar(128),
    version bigint not null default 0
);

create index ix_communication_outbox_ready
    on communication_outbox_event (occurred_at, id) where published_at is null;

create or replace function reject_communication_immutable_mutation()
returns trigger language plpgsql as $$
begin
    raise exception 'communication history is append-only';
end;
$$;

create trigger trg_conversation_message_immutable
before update or delete on conversation_message
for each row execute function reject_communication_immutable_mutation();

create trigger trg_communication_audit_immutable
before update or delete on communication_audit_event
for each row execute function reject_communication_immutable_mutation();
