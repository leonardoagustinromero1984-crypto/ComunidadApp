-- LeoVer Canonical Baseline
-- Logical migration: 1104
-- Retire a reunited provisional FOUND VitaCora. Do not delete it.
-- Does not edit 1000–1103. Does not grant table SELECT. Does not apply itself.
--
-- Permanent rule: when a provisional FOUND pet is confirmed as an existing LOST pet,
-- the LOST pet and its VitaCora stay canonical. Hallazgo moments already move there.
-- The provisional pet stays ARCHIVED. Its vitacora_profiles row stays only as
-- historical identity: retired_at and successor_pet_id are set together, and
-- public_vitacora_number is kept and never reused.
-- The historical number redirects to the successor. Writes do not.
--
-- Expected after application, not special-cased here:
-- survivor  f58305a1-0b83-40ed-bbc3-fdcdc0120fb5
-- provisional 5f487c6f-f7b5-4b68-9419-d018b89195f8
--
-- RPC classification:
-- SAFE_UNCHANGED:
--   canon_public_pet, canon_public_lost_found
--   canon_search_vitacora_access_targets
--   memory reads that only check vitacora.view
--   canon_complete_foster_transit, canon_record_vet_care, canon_daycare_checkout
--   found-identity creators (they insert a new, non-retired profile)
-- MUST_REJECT_RETIRED:
--   canon_list_vitacora_moments
--   canon_create_moment, canon_save_social_vitacora_moment
--   canon_grant_vitacora, canon_revoke_vitacora
--   canon_create_proposal, canon_decide_proposal, canon_hide_integration
--   canon_list_vitacora_grants, canon_list_vitacora_proposals
--   canon_attach_lost_found_photo (moment insert)
-- MUST_REDIRECT_TO_SUCCESSOR:
--   canon_search_vitacora_number
-- ADMIN_HISTORICAL_ONLY:
--   the retained vitacora_profiles row, security_audit_events,
--   pet_responsibility_events, pet_lifecycle_events
--   Staff search returns the successor, not a second VitaCora.
--   Admin list bypass does not synthesize CARE_CREATED.

alter table public.vitacora_profiles
  add column if not exists retired_at timestamptz null;

alter table public.vitacora_profiles
  add column if not exists successor_pet_id uuid null;

do $$
begin
  if not exists (
    select 1
      from pg_constraint
     where conname = 'vitacora_profiles_successor_pet_fk'
       and conrelid = 'public.vitacora_profiles'::regclass
  ) then
    alter table public.vitacora_profiles
      add constraint vitacora_profiles_successor_pet_fk
      foreign key (successor_pet_id) references public.pets(id);
  end if;
end;
$$;

alter table public.vitacora_profiles
  drop constraint if exists vitacora_profiles_retirement_pair_chk;

alter table public.vitacora_profiles
  add constraint vitacora_profiles_retirement_pair_chk
  check (
    (retired_at is null and successor_pet_id is null)
    or (
      retired_at is not null
      and successor_pet_id is not null
      and successor_pet_id <> pet_id
    )
  );

create index if not exists vitacora_profiles_successor_pet_idx
  on public.vitacora_profiles (successor_pet_id)
  where successor_pet_id is not null;

create or replace function public._canon_assert_vitacora_not_retired(p_pet_id uuid)
returns void
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_pet_id is not null and exists (
    select 1
      from public.vitacora_profiles v
     where v.pet_id = p_pet_id
       and v.retired_at is not null
  ) then
    raise exception 'VITACORA_RETIRED';
  end if;
end;
$$;

create or replace function public._canon_retire_reunified_vitacora(
  p_archived_pet_id uuid,
  p_survivor_pet_id uuid,
  p_actor uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link_id uuid;
  v_actor uuid;
begin
  if p_archived_pet_id is null or p_survivor_pet_id is null then
    return;
  end if;
  if p_archived_pet_id = p_survivor_pet_id then
    return;
  end if;
  if not exists (select 1 from public.pets p where p.id = p_survivor_pet_id) then
    return;
  end if;
  if not exists (
    select 1
      from public.pets p
     where p.id = p_archived_pet_id
       and p.origin_kind = 'FOUND_CASE'
       and p.lifecycle_status = 'ARCHIVED'
  ) then
    return;
  end if;
  if not exists (
    select 1 from public.vitacora_profiles v where v.pet_id = p_archived_pet_id
  ) then
    return;
  end if;
  if exists (
    select 1
      from public.vitacora_profiles v
     where v.pet_id = p_archived_pet_id
       and v.retired_at is not null
       and v.successor_pet_id is distinct from p_survivor_pet_id
  ) then
    return;
  end if;

  update public.vitacora_profiles
     set retired_at = coalesce(retired_at, timezone('utc', now())),
         successor_pet_id = p_survivor_pet_id
   where pet_id = p_archived_pet_id
     and (
       retired_at is null
       or successor_pet_id = p_survivor_pet_id
     );

  v_actor := coalesce(
    p_actor,
    (select p.created_by_user_id from public.pets p where p.id = p_archived_pet_id)
  );
  if not exists (select 1 from public.persons where user_id = v_actor) then
    v_actor := (select p.created_by_user_id from public.pets p where p.id = p_archived_pet_id);
  end if;

  for v_link_id in
    select l.id
      from public.pet_responsibility_links l
     where l.pet_id = p_archived_pet_id
       and l.holder_kind = 'PERSON'
       and l.role = 'AUTHORIZED'
       and l.status = 'ACTIVE'
       and exists (
         select 1
           from public.pet_responsibility_events ev
          where ev.link_id = l.id
            and ev.pet_id = p_archived_pet_id
            and ev.event_type = 'FOUND_CASE_CARE'
       )
     for update
  loop
    update public.pet_responsibility_links
       set status = 'ENDED',
           valid_until = timezone('utc', now())
     where id = v_link_id
       and status = 'ACTIVE';
    if exists (select 1 from public.persons where user_id = v_actor) then
      insert into public.pet_responsibility_events (
        pet_id, link_id, actor_user_id, event_type, metadata
      ) values (
        p_archived_pet_id,
        v_link_id,
        v_actor,
        'ENDED',
        jsonb_build_object(
          'source', 'vitacora_reunification_retirement',
          'survivor_pet_id', p_survivor_pet_id
        )
      );
    end if;
  end loop;

  update public.pet_permission_grants g
     set revoked_at = timezone('utc', now())
    from public.pet_responsibility_links l
   where g.link_id = l.id
     and g.revoked_at is null
     and l.pet_id = p_archived_pet_id
     and l.holder_kind = 'PERSON'
     and l.role = 'AUTHORIZED'
     and exists (
       select 1
         from public.pet_responsibility_events ev
        where ev.link_id = l.id
          and ev.pet_id = p_archived_pet_id
          and ev.event_type = 'FOUND_CASE_CARE'
     );

  update public.vitacora_access_grants
     set revoked_at = timezone('utc', now()),
         revoked_by = case
           when exists (select 1 from public.persons where user_id = p_actor) then p_actor
           else revoked_by
         end
   where pet_id = p_archived_pet_id
     and revoked_at is null;
end;
$$;

create or replace function public._vitacora_reject_retired_write()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.pet_id is not null and exists (
    select 1
      from public.vitacora_profiles v
     where v.pet_id = new.pet_id
       and v.retired_at is not null
  ) then
    raise exception 'VITACORA_RETIRED';
  end if;
  return new;
end;
$$;

drop trigger if exists vitacora_moments_reject_retired_write on public.vitacora_moments;
create trigger vitacora_moments_reject_retired_write
  before insert or update on public.vitacora_moments
  for each row execute function public._vitacora_reject_retired_write();

drop trigger if exists vitacora_proposals_reject_retired_write on public.vitacora_update_proposals;
create trigger vitacora_proposals_reject_retired_write
  before insert or update on public.vitacora_update_proposals
  for each row execute function public._vitacora_reject_retired_write();

drop trigger if exists vitacora_access_grants_reject_retired_insert on public.vitacora_access_grants;
create trigger vitacora_access_grants_reject_retired_insert
  before insert on public.vitacora_access_grants
  for each row execute function public._vitacora_reject_retired_write();

drop trigger if exists pet_permission_grants_reject_retired_insert on public.pet_permission_grants;
create trigger pet_permission_grants_reject_retired_insert
  before insert on public.pet_permission_grants
  for each row execute function public._vitacora_reject_retired_write();

create or replace function public.canon_confirm_found_owner_match(p_candidate_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_cand public.lost_found_match_candidates%rowtype;
  v_found public.lost_found_alerts%rowtype;
  v_lost public.lost_found_alerts%rowtype;
  v_found_pet uuid;
  v_lost_pet uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_cand from public.lost_found_match_candidates where id = p_candidate_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  select * into v_found from public.lost_found_alerts where id = v_cand.alert_id for update;
  select * into v_lost from public.lost_found_alerts where id = v_cand.lost_alert_id for update;
  if v_found.pet_id is null or v_lost.pet_id is null then raise exception 'NOT_FOUND'; end if;
  v_found_pet := v_found.pet_id;
  v_lost_pet := v_lost.pet_id;
  if not exists (
    select 1 from public.pets p
     where p.id = v_found_pet
       and p.current_custodian_person_id = auth.uid()
  ) then
    raise exception 'FORBIDDEN';
  end if;

  update public.pets lost
     set avatar_asset_id = coalesce(lost.avatar_asset_id, found.avatar_asset_id),
         updated_at = timezone('utc', now())
    from public.pets found
   where lost.id = v_lost_pet
     and found.id = v_found_pet;
  update public.vitacora_moments set pet_id = v_lost_pet where pet_id = v_found_pet;
  update public.lost_found_alerts
     set pet_id = v_lost_pet,
         status = 'RESOLVED',
         resolved_at = timezone('utc', now()),
         updated_at = timezone('utc', now())
   where id = v_found.id;
  update public.lost_found_alerts
     set status = 'RESOLVED',
         resolved_at = timezone('utc', now()),
         updated_at = timezone('utc', now())
   where id = v_lost.id;
  update public.pets
     set lifecycle_status = 'ARCHIVED',
         archived_at = timezone('utc', now()),
         updated_at = timezone('utc', now())
   where id = v_found_pet;
  perform public._canon_retire_reunified_vitacora(v_found_pet, v_lost_pet, auth.uid());
  update public.lost_found_match_candidates
     set status = 'ACCEPTED'
   where id = p_candidate_id;
  update public.lost_found_match_candidates
     set status = 'REJECTED'
   where alert_id = v_found.id
     and id is distinct from p_candidate_id
     and status = 'PENDING';
  perform public._canon_stop_lost_found_fanout(v_found.id);
  insert into public.lost_found_match_reviews (candidate_id, reviewer_user_id, decision)
  values (p_candidate_id, auth.uid(), 'ACCEPTED');
  perform public.canon_audit(
    'lost_found.reunify',
    'lost_found_alerts',
    v_found.id,
    jsonb_build_object('survivor_pet_id', v_lost_pet, 'archived_pet_id', v_found_pet)
  );
  return jsonb_build_object(
    'found_id', v_found.id,
    'lost_id', v_lost.id,
    'pet_id', v_lost_pet,
    'archived_pet_id', v_found_pet
  );
end;
$$;

create or replace function public.canon_list_vitacora_moments(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.view')
    or public._acl_is_admin(auth.uid())
  ) then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(p_pet_id);
  return coalesce((
    select jsonb_agg(row_data order by (row_data->>'created_at') desc)
    from (
      select jsonb_build_object(
        'id', m.id,
        'kind', m.kind,
        'title', m.title,
        'body', case
          when a.id is not null
           and public._canon_personal_media_withheld_from(auth.uid(), a)
          then null
          else m.body
        end,
        'created_by', m.created_by,
        'created_at', m.created_at
      ) as row_data
      from public.vitacora_moments m
      left join public.media_assets a
        on a.id = public._canon_moment_linked_asset_id(m)
      where m.pet_id = p_pet_id
        and m.hidden_at is null

      union all

      select jsonb_build_object(
        'id', p.id,
        'kind', 'CARE_CREATED',
        'title', 'Se creó la VitaCora de ' || p.name || '.',
        'body', null,
        'created_by', p.created_by_user_id,
        'created_at', p.created_at
      )
      from public.pets p
      where p.id = p_pet_id

      union all

      select jsonb_build_object(
        'id', t.id,
        'kind', 'CARE_TRANSFER',
        'title',
          p.name || ' pasó a estar bajo el cuidado de ' ||
          coalesce(
            public._canon_actor_display_name(
              t.target_kind, t.target_person_id, t.target_organization_id
            ),
            'un nuevo cuidador'
          ) || '.',
        'body', null,
        'created_by', t.decided_by_user_id,
        'created_at', t.decided_at
      )
      from public.pet_care_transfers t
      join public.pets p on p.id = t.pet_id
      where t.pet_id = p_pet_id
        and t.status = 'ACCEPTED'
        and t.decided_at is not null
    ) listed
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_create_moment(
  p_pet_id uuid, p_kind text, p_title text, p_body text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.manage') then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(p_pet_id);
  insert into public.vitacora_moments (pet_id, kind, title, body, created_by)
  values (p_pet_id, p_kind, p_title, p_body, auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_save_social_vitacora_moment(
  p_pet_id uuid,
  p_title text,
  p_body text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_source uuid;
  v_tmp public.vitacora_moments%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.manage') then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(p_pet_id);
  v_tmp.body := p_body;
  v_source := public._canon_moment_source_social_post_id(v_tmp);
  if v_source is not null then
    select m.id into v_id
    from public.vitacora_moments m
    where m.pet_id = p_pet_id
      and m.kind = 'SOCIAL'
      and m.hidden_at is null
      and public._canon_moment_source_social_post_id(m) = v_source
    order by m.created_at desc
    limit 1;
    if v_id is not null then
      update public.vitacora_moments
      set title = coalesce(nullif(btrim(p_title), ''), title),
          body = p_body
      where id = v_id;
      return v_id;
    end if;
  end if;
  insert into public.vitacora_moments (pet_id, kind, title, body, created_by)
  values (p_pet_id, 'SOCIAL', p_title, p_body, auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_grant_vitacora(
  p_pet_id uuid, p_kind text, p_person uuid, p_org uuid,
  p_purpose text, p_scope text, p_expires timestamptz
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.share') then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(p_pet_id);
  if p_scope = 'FULL_SHAREABLE' and not public._acl_age_allows(auth.uid(), 'vitacora.grant_full_shareable') then
    raise exception 'AGE_CAPABILITY_DENIED';
  end if;
  insert into public.vitacora_access_grants (
    pet_id, grantee_kind, grantee_person_id, grantee_organization_id,
    purpose, scope, granted_by_actor_user_id, expires_at
  ) values (p_pet_id, p_kind, p_person, p_org, p_purpose, p_scope, auth.uid(), p_expires)
  returning id into v_id;
  perform public.canon_audit('vitacora.grant', 'vitacora_access_grants', v_id, jsonb_build_object('scope', p_scope));
  return v_id;
end;
$$;

create or replace function public.canon_revoke_vitacora(p_grant_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  g public.vitacora_access_grants%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_grant_id is null then raise exception 'VALIDATION'; end if;
  select * into g from public.vitacora_access_grants where id = p_grant_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if not public._acl_pet_permission(auth.uid(), g.pet_id, 'vitacora.share') then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(g.pet_id);
  update public.vitacora_access_grants
    set revoked_at = timezone('utc', now()), revoked_by = auth.uid()
   where id = p_grant_id and revoked_at is null;
end;
$$;

create or replace function public.canon_create_proposal(
  p_pet_id uuid, p_origin text, p_payload jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_pet_id is null then raise exception 'VALIDATION'; end if;
  if not (
    public._acl_pet_holder(auth.uid(), p_pet_id)
    or public._acl_grant_active(auth.uid(), p_pet_id, 'ESSENTIAL')
    or public._acl_grant_active(auth.uid(), p_pet_id, 'HEALTH')
  ) then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(p_pet_id);
  insert into public.vitacora_update_proposals (pet_id, origin_kind, payload, actor_user_id)
  values (p_pet_id, p_origin, p_payload, auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_decide_proposal(p_proposal_id uuid, p_status text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare v_prop public.vitacora_update_proposals%rowtype;
begin
  select * into v_prop from public.vitacora_update_proposals where id = p_proposal_id;
  if not public._acl_pet_permission(auth.uid(), v_prop.pet_id, 'vitacora.manage') then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(v_prop.pet_id);
  if p_status not in ('ACCEPTED', 'REJECTED', 'CORRECTION_REQUESTED') then
    raise exception 'INVALID_STATUS';
  end if;
  update public.vitacora_update_proposals
    set status = p_status, decided_by = auth.uid(), decided_at = timezone('utc', now())
    where id = p_proposal_id;
  if p_status = 'ACCEPTED' and v_prop.payload ? 'allergy' then
    insert into public.pet_allergies (pet_id, name, source, actor_user_id)
    values (v_prop.pet_id, v_prop.payload->>'allergy', 'THIRD_PARTY', auth.uid());
    insert into public.vitacora_integration_links (
      pet_id, source_table, source_record_id, created_by
    ) values (v_prop.pet_id, 'pet_allergies', p_proposal_id, auth.uid());
  end if;
end;
$$;

create or replace function public.canon_hide_integration(p_link_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pet uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_link_id is null then raise exception 'VALIDATION'; end if;
  select pet_id into v_pet from public.vitacora_integration_links where id = p_link_id;
  if v_pet is null then raise exception 'NOT_FOUND'; end if;
  if not public._acl_pet_permission(auth.uid(), v_pet, 'vitacora.manage') then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(v_pet);
  update public.vitacora_integration_links
     set visible = false, hidden_at = timezone('utc', now())
   where id = p_link_id;
end;
$$;

create or replace function public.canon_list_vitacora_grants(p_pet_id uuid)
returns setof public.vitacora_access_grants
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.view') then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(p_pet_id);
  return query
    select *
    from public.vitacora_access_grants g
    where g.pet_id = p_pet_id
    order by g.granted_at desc;
end;
$$;

create or replace function public.canon_list_vitacora_proposals(p_pet_id uuid)
returns setof public.vitacora_update_proposals
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.view') then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(p_pet_id);
  return query
    select *
    from public.vitacora_update_proposals p
    where p.pet_id = p_pet_id
    order by p.created_at desc;
end;
$$;

create or replace function public.canon_attach_lost_found_photo(p_id uuid, p_photo_asset_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.lost_found_alerts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_photo_asset_id is null then raise exception 'PHOTO_REQUIRED'; end if;
  select * into v_row from public.lost_found_alerts where id = p_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_row.created_by <> auth.uid()
     and not exists (
       select 1 from public.pets p
        where p.id = v_row.pet_id and p.current_custodian_person_id = auth.uid()
     ) then
    raise exception 'FORBIDDEN';
  end if;
  update public.lost_found_alerts
     set photo_asset_id = p_photo_asset_id,
         updated_at = timezone('utc', now())
   where id = p_id;
  if v_row.pet_id is not null then
    update public.pets
       set avatar_asset_id = coalesce(avatar_asset_id, p_photo_asset_id),
           updated_at = timezone('utc', now())
     where id = v_row.pet_id;
    if not exists (
      select 1 from public.vitacora_moments m
       where m.pet_id = v_row.pet_id and m.asset_id = p_photo_asset_id
    ) then
      perform public._canon_assert_vitacora_not_retired(v_row.pet_id);
      insert into public.vitacora_moments (pet_id, kind, title, asset_id, created_by, occurred_on)
      values (v_row.pet_id, 'PHOTO', 'Foto del hallazgo', p_photo_asset_id, auth.uid(), current_date);
    end if;
  end if;
  return true;
end;
$$;

create or replace function public.canon_search_vitacora_number(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_num bigint;
  v_q text := btrim(p_query);
  v_profile public.vitacora_profiles%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  v_q := regexp_replace(regexp_replace(v_q, '^#', ''), '^[Vv]ita[Cc]ora\s*#?\s*', '');
  begin
    v_num := v_q::bigint;
  exception
    when others then
      return '[]'::jsonb;
  end;

  select * into v_profile
    from public.vitacora_profiles
   where public_vitacora_number = v_num;
  if not found then
    return '[]'::jsonb;
  end if;

  if v_profile.retired_at is null then
    return coalesce((
      select jsonb_agg(jsonb_build_object(
        'pet_id', p.id,
        'name', p.name,
        'public_vitacora_number', v.public_vitacora_number,
        'needs_photo', p.avatar_asset_id is null
      ))
      from public.vitacora_profiles v
      join public.pets p on p.id = v.pet_id
      where v.pet_id = v_profile.pet_id
        and v.retired_at is null
        and (public._acl_pet_holder(auth.uid(), p.id) or public._acl_is_staff(auth.uid()))
    ), '[]'::jsonb);
  end if;

  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'pet_id', p.id,
      'name', p.name,
      'public_vitacora_number', v.public_vitacora_number,
      'needs_photo', p.avatar_asset_id is null
    ))
    from public.pets p
    join public.vitacora_profiles v on v.pet_id = p.id
    where p.id = v_profile.successor_pet_id
      and v.retired_at is null
      and (public._acl_pet_holder(auth.uid(), p.id) or public._acl_is_staff(auth.uid()))
  ), '[]'::jsonb);
end;
$$;

-- Backfill already-confirmed reunifications from lost_found.reunify.
-- Malformed metadata is skipped. A conflicting successor is not overwritten.
-- Duplicate audit rows are applied in occurred_at, id order and then no-op.
do $$
declare
  r record;
  v_archived uuid;
  v_survivor uuid;
begin
  for r in
    select e.id, e.actor_user_id, e.metadata
      from public.security_audit_events e
     where e.action = 'lost_found.reunify'
     order by e.occurred_at asc, e.id asc
  loop
    begin
      v_archived := nullif(btrim(r.metadata->>'archived_pet_id'), '')::uuid;
      v_survivor := nullif(btrim(r.metadata->>'survivor_pet_id'), '')::uuid;
    exception
      when invalid_text_representation then
        continue;
    end;
    if v_archived is null or v_survivor is null then
      continue;
    end if;
    perform public._canon_retire_reunified_vitacora(v_archived, v_survivor, r.actor_user_id);
  end loop;
end;
$$;

revoke all on table public.vitacora_profiles from public, anon, authenticated;

revoke all on function public._canon_assert_vitacora_not_retired(uuid) from public, anon, authenticated;
revoke all on function public._canon_retire_reunified_vitacora(uuid, uuid, uuid) from public, anon, authenticated;
revoke all on function public._vitacora_reject_retired_write() from public, anon, authenticated;

revoke all on function public.canon_confirm_found_owner_match(uuid) from public, anon;
grant execute on function public.canon_confirm_found_owner_match(uuid) to authenticated;

revoke all on function public.canon_list_vitacora_moments(uuid) from public, anon;
grant execute on function public.canon_list_vitacora_moments(uuid) to authenticated;

revoke all on function public.canon_create_moment(uuid, text, text, text) from public, anon;
grant execute on function public.canon_create_moment(uuid, text, text, text) to authenticated;

revoke all on function public.canon_save_social_vitacora_moment(uuid, text, text) from public, anon;
grant execute on function public.canon_save_social_vitacora_moment(uuid, text, text) to authenticated;

revoke all on function public.canon_grant_vitacora(uuid, text, uuid, uuid, text, text, timestamptz) from public, anon;
grant execute on function public.canon_grant_vitacora(uuid, text, uuid, uuid, text, text, timestamptz) to authenticated;

revoke all on function public.canon_revoke_vitacora(uuid) from public, anon;
grant execute on function public.canon_revoke_vitacora(uuid) to authenticated;

revoke all on function public.canon_create_proposal(uuid, text, jsonb) from public, anon;
grant execute on function public.canon_create_proposal(uuid, text, jsonb) to authenticated;

revoke all on function public.canon_decide_proposal(uuid, text) from public, anon;
grant execute on function public.canon_decide_proposal(uuid, text) to authenticated;

revoke all on function public.canon_hide_integration(uuid) from public, anon;
grant execute on function public.canon_hide_integration(uuid) to authenticated;

revoke all on function public.canon_list_vitacora_grants(uuid) from public, anon;
grant execute on function public.canon_list_vitacora_grants(uuid) to authenticated;

revoke all on function public.canon_list_vitacora_proposals(uuid) from public, anon;
grant execute on function public.canon_list_vitacora_proposals(uuid) to authenticated;

revoke all on function public.canon_attach_lost_found_photo(uuid, uuid) from public, anon;
grant execute on function public.canon_attach_lost_found_photo(uuid, uuid) to authenticated;

revoke all on function public.canon_search_vitacora_number(text) from public, anon;
grant execute on function public.canon_search_vitacora_number(text) to authenticated;

notify pgrst, 'reload schema';
