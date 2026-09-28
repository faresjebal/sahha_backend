-- Communication stores an immutable reference, never writes Clinical's database.
-- NULL preserves existing appointment-authorised commands; no cross-database FK.
alter table conversation_thread add column source_consultation_id uuid;
alter table referral_request add column source_consultation_id uuid;
alter table conversation_thread add constraint ck_conversation_source_patient
    check (source_consultation_id is null or patient_registration_id is not null);

create function reject_collaboration_source_change() returns trigger language plpgsql as $$
begin
    if new.source_consultation_id is distinct from old.source_consultation_id then
        raise exception 'collaboration source is immutable';
    end if;
    return new;
end;
$$;
create trigger trg_conversation_source_immutable before update on conversation_thread
    for each row execute function reject_collaboration_source_change();
create trigger trg_referral_source_immutable before update on referral_request
    for each row execute function reject_collaboration_source_change();
