-- Never upgrade an existing selected-resource grant to whole-history access.
alter table referral_request add column referral_type varchar(32)
    not null default 'SECOND_OPINION';
alter table referral_request add constraint ck_referral_type
    check (referral_type in ('SECOND_OPINION', 'SHARED_TREATMENT'));

create function reject_referral_type_change() returns trigger language plpgsql as $$
begin
    if new.referral_type is distinct from old.referral_type then
        raise exception 'referral consent scope is immutable';
    end if;
    return new;
end;
$$;
create trigger trg_referral_type_immutable before update on referral_request
    for each row execute function reject_referral_type_change();

-- Do not infer participation from a selected item or a conversation. Both doctors
-- are recorded explicitly on acceptance. Grant/referral termination stops access
-- without deleting this history or affecting independent care authorities.
create table referral_care_participation (
    id uuid primary key,
    grant_id uuid not null references referral_sharing_grant(id),
    doctor_user_id uuid not null,
    doctor_membership_id uuid not null,
    started_at timestamptz not null,
    constraint uq_referral_care_participant unique (grant_id, doctor_user_id)
);
create index ix_referral_care_doctor
    on referral_care_participation (doctor_user_id, doctor_membership_id, grant_id);

create function validate_referral_care_participant() returns trigger language plpgsql as $$
begin
    if not exists (
        select 1 from referral_sharing_grant g join referral_request r on r.id = g.referral_id
        where g.id = new.grant_id and g.status = 'ACTIVE' and r.status = 'ACTIVE'
          and r.referral_type = 'SHARED_TREATMENT'
          and g.organisation_id = r.organisation_id
          and g.patient_registration_id = r.patient_registration_id
          and g.recipient_user_id = r.recipient_user_id
          and g.recipient_membership_id = r.recipient_membership_id
          and g.valid_from = new.started_at and g.valid_until = r.access_expires_at
          and ((new.doctor_user_id = r.sender_user_id and new.doctor_membership_id = r.sender_membership_id)
            or (new.doctor_user_id = r.recipient_user_id and new.doctor_membership_id = r.recipient_membership_id))
    ) then
        raise exception 'care participation requires an accepted shared-treatment grant';
    end if;
    return new;
end;
$$;
create trigger trg_referral_care_valid before insert on referral_care_participation
    for each row execute function validate_referral_care_participant();
create trigger trg_referral_care_immutable before update or delete on referral_care_participation
    for each row execute function reject_communication_immutable_mutation();
