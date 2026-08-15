-- LeoVer Canonical Baseline
-- Logical migration: 1020
-- ACL helpers, RLS, RPCs, storage buckets. ActiveContext is never security.

-- ---------------------------------------------------------------------------
-- ACL helpers (SECURITY DEFINER, fixed search_path, no recursion)
-- ---------------------------------------------------------------------------

create or replace function public._acl_platform_role(p_user_id uuid, p_role text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.user_platform_role_assignments a
    where a.user_id = p_user_id and a.role_code = p_role and a.revoked_at is null
  );
$$;

create or replace function public._acl_is_admin(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public._acl_platform_role(p_user_id, 'ADMIN')
      or public._acl_platform_role(p_user_id, 'SUPERADMIN');
$$;

create or replace function public._acl_org_member(p_user_id uuid, p_org_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.organization_memberships m
    where m.person_id = p_user_id
      and m.organization_id = p_org_id
      and m.status = 'ACTIVE'
  );
$$;

create or replace function public._acl_org_permission(p_user_id uuid, p_org_id uuid, p_code text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.organization_memberships m
    join public.organization_role_permissions rp on rp.role_id = m.role_id
    where m.person_id = p_user_id
      and m.organization_id = p_org_id
      and m.status = 'ACTIVE'
      and rp.permission_code = p_code
  );
$$;

create or replace function public._acl_pet_holder(p_user_id uuid, p_pet_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.pet_responsibility_links l
    where l.pet_id = p_pet_id
      and l.status = 'ACTIVE'
      and (
        l.holder_person_id = p_user_id
        or (
          l.holder_kind = 'ORGANIZATION'
          and public._acl_org_member(p_user_id, l.holder_organization_id)
        )
      )
  );
$$;

create or replace function public._acl_pet_permission(p_user_id uuid, p_pet_id uuid, p_code text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public._acl_pet_holder(p_user_id, p_pet_id)
     and (
       exists (
         select 1 from public.pet_permission_grants g
         where g.pet_id = p_pet_id
           and g.revoked_at is null
           and g.permission_code = p_code
           and (g.subject_person_id = p_user_id or (
             g.subject_organization_id is not null
             and public._acl_org_member(p_user_id, g.subject_organization_id)
           ))
       )
       or exists (
         select 1 from public.pet_responsibility_links l
         where l.pet_id = p_pet_id
           and l.status = 'ACTIVE'
           and l.holder_person_id = p_user_id
           and l.role = 'OWNER'
       )
     );
$$;

create or replace function public._acl_age_allows(p_user_id uuid, p_action text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_band text;
  v_rule public.age_capability_rules%rowtype;
  v_has_guardian boolean;
begin
  select public.person_age_band(p.birth_date) into v_band
  from public.persons p where p.user_id = p_user_id;
  if v_band is null or v_band = 'UNDER_13' then
    return false;
  end if;
  select * into v_rule from public.age_capability_rules where action_code = p_action;
  if not found then
    return v_band = 'ADULT_18_PLUS';
  end if;
  if v_band = 'TEEN_13_15' and v_rule.min_age_band in ('TEEN_16_17', 'ADULT_18_PLUS') then
    return false;
  end if;
  if v_band = 'TEEN_16_17' and v_rule.min_age_band = 'ADULT_18_PLUS' then
    return false;
  end if;
  if v_rule.requires_guardian_confirmation and v_band <> 'ADULT_18_PLUS' then
    select exists (
      select 1 from public.guardian_relationships g
      where g.minor_user_id = p_user_id and g.status = 'ACTIVE'
    ) into v_has_guardian;
    if not v_has_guardian then
      return false;
    end if;
  end if;
  return true;
end;
$$;

create or replace function public._acl_grant_active(p_user_id uuid, p_pet_id uuid, p_need text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.vitacora_access_grants g
    where g.pet_id = p_pet_id
      and g.revoked_at is null
      and (g.expires_at is null or g.expires_at > timezone('utc', now()))
      and (
        g.grantee_person_id = p_user_id
        or (g.grantee_organization_id is not null and public._acl_org_member(p_user_id, g.grantee_organization_id))
      )
      and (
        p_need = 'ESSENTIAL'
        or (p_need = 'HEALTH' and g.scope in ('HEALTH', 'ESSENTIAL_AND_HEALTH', 'FULL_SHAREABLE'))
        or (p_need = 'FULL' and g.scope = 'FULL_SHAREABLE')
      )
  );
$$;

-- ---------------------------------------------------------------------------
-- RLS enable + catalog read
-- ---------------------------------------------------------------------------

do $$
declare r record;
begin
  for r in select tablename from pg_tables where schemaname = 'public'
  loop
    execute format('alter table public.%I enable row level security', r.tablename);
    execute format('revoke all on table public.%I from anon, authenticated', r.tablename);
  end loop;
end$$;

grant usage on schema public to anon, authenticated;

grant select on table
  public.location_nodes,
  public.species,
  public.breeds,
  public.permission_codes,
  public.service_categories,
  public.care_event_types,
  public.moderation_reason_codes,
  public.platform_roles,
  public.legal_documents
to anon, authenticated;

create policy location_nodes_read on public.location_nodes for select using (active);
create policy species_read on public.species for select using (active);
create policy breeds_read on public.breeds for select using (active);
create policy permission_codes_read on public.permission_codes for select using (active);
create policy service_categories_read on public.service_categories for select using (active);
create policy care_event_types_read on public.care_event_types for select using (active);
create policy moderation_reason_codes_read on public.moderation_reason_codes for select using (active);
create policy platform_roles_read on public.platform_roles for select using (true);
create policy legal_documents_read on public.legal_documents for select
  using (status in ('DRAFT', 'EFFECTIVE'));

create policy persons_self_select on public.persons for select
  using (user_id = auth.uid() or public._acl_is_admin(auth.uid()));
create policy persons_self_update on public.persons for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy pets_holder_select on public.pets for select
  using (
    public._acl_pet_holder(auth.uid(), id)
    or public._acl_grant_active(auth.uid(), id, 'ESSENTIAL')
    or public._acl_is_admin(auth.uid())
  );

create policy vitacora_moments_private on public.vitacora_moments for select
  using (
    public._acl_pet_permission(auth.uid(), pet_id, 'vitacora.view')
    or public._acl_is_admin(auth.uid())
  );

create policy messages_participants_select on public.messages for select
  using (
    exists (
      select 1 from public.conversation_participants p
      where p.conversation_id = messages.conversation_id
        and p.left_at is null
        and (
          p.person_id = auth.uid()
          or (p.organization_id is not null and public._acl_org_member(auth.uid(), p.organization_id))
        )
    )
  );

create policy brand_placements_adults_only on public.brand_placements for select
  using (
    exists (
      select 1 from public.persons per
      where per.user_id = auth.uid()
        and public.person_age_band(per.birth_date) = 'ADULT_18_PLUS'
    )
  );

-- ---------------------------------------------------------------------------
-- Storage buckets
-- ---------------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit)
values
  ('public-media', 'public-media', true, 8388608),
  ('private-media', 'private-media', false, 15728640),
  ('documents', 'documents', false, 15728640),
  ('moderation-evidence', 'moderation-evidence', false, 15728640)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit;

-- ---------------------------------------------------------------------------
-- Domain RPCs
-- ---------------------------------------------------------------------------

create or replace function public.canon_audit(
  p_action text, p_table text, p_id uuid, p_meta jsonb default '{}'::jsonb
)
returns void
language sql
security definer
set search_path = public
as $$
  insert into public.security_audit_events (actor_user_id, action, entity_table, entity_id, metadata)
  values (auth.uid(), p_action, p_table, p_id, coalesce(p_meta, '{}'::jsonb));
$$;

create or replace function public.canon_create_organization(
  p_name text, p_slug text, p_capability text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_role uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_age_allows(auth.uid(), 'org.create') then
    raise exception 'AGE_CAPABILITY_DENIED';
  end if;
  insert into public.organizations (name, slug, created_by_user_id)
  values (p_name, p_slug, auth.uid())
  returning id into v_id;
  insert into public.organization_capabilities (organization_id, capability)
  values (v_id, p_capability);
  insert into public.organization_roles (organization_id, code, name, is_system)
  values (v_id, 'OWNER', 'Owner', true)
  returning id into v_role;
  insert into public.organization_role_permissions (role_id, permission_code)
  select v_role, code from public.permission_codes where scope = 'ORG';
  insert into public.organization_memberships (organization_id, person_id, role_id, status)
  values (v_id, auth.uid(), v_role, 'ACTIVE');
  insert into public.organization_public_profiles (organization_id) values (v_id);
  perform public.canon_audit('org.create', 'organizations', v_id, '{}'::jsonb);
  return v_id;
end;
$$;

create or replace function public.canon_create_pet(
  p_name text,
  p_species text,
  p_birth_precision text default 'UNKNOWN',
  p_birth_date date default null,
  p_birth_year integer default null,
  p_birth_month integer default null,
  p_estimated_age_months integer default null,
  p_estimated_as_of date default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_link uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  insert into public.pets (
    created_by_user_id, name, species_code, birth_precision,
    birth_date, birth_year, birth_month, estimated_age_months, estimated_as_of
  ) values (
    auth.uid(), p_name, p_species, p_birth_precision,
    p_birth_date, p_birth_year, p_birth_month, p_estimated_age_months, p_estimated_as_of
  ) returning id into v_id;
  insert into public.vitacora_profiles (pet_id) values (v_id);
  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
  ) values (v_id, 'PERSON', auth.uid(), 'OWNER', auth.uid())
  returning id into v_link;
  insert into public.pet_permission_grants (pet_id, link_id, subject_person_id, permission_code, granted_by)
  select v_id, v_link, auth.uid(), code, auth.uid()
  from public.permission_codes where scope in ('PET', 'VITACORA');
  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (v_id, v_link, auth.uid(), 'CREATED_OWNER');
  insert into public.pet_lifecycle_events (pet_id, to_status, actor_user_id)
  values (v_id, 'ACTIVE', auth.uid());
  return v_id;
end;
$$;

create or replace function public.canon_add_pet_person(
  p_pet_id uuid, p_person_id uuid, p_role text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_link uuid;
begin
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'responsibility.manage') then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
  ) values (p_pet_id, 'PERSON', p_person_id, p_role, auth.uid())
  returning id into v_link;
  insert into public.pet_permission_grants (pet_id, link_id, subject_person_id, permission_code, granted_by)
  select p_pet_id, v_link, p_person_id, code, auth.uid()
  from public.permission_codes
  where scope in ('PET', 'VITACORA')
    and (p_role = 'OWNER' or code in ('pet.view', 'vitacora.view'));
  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type, metadata)
  values (p_pet_id, v_link, auth.uid(), 'ADDED_PERSON', jsonb_build_object('role', p_role));
  return v_link;
end;
$$;

create or replace function public.canon_set_org_responsible(p_pet_id uuid, p_org_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_link uuid;
begin
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'responsibility.manage')
     and not public._acl_org_permission(auth.uid(), p_org_id, 'org.pets.manage') then
    raise exception 'FORBIDDEN';
  end if;
  update public.pet_responsibility_links
    set status = 'ENDED', valid_until = timezone('utc', now())
    where pet_id = p_pet_id and holder_kind = 'ORGANIZATION' and status = 'ACTIVE';
  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_organization_id, role, granted_by_actor_user_id
  ) values (p_pet_id, 'ORGANIZATION', p_org_id, 'RESPONSIBLE', auth.uid())
  returning id into v_link;
  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (p_pet_id, v_link, auth.uid(), 'ORG_RESPONSIBLE');
  return v_link;
end;
$$;

create or replace function public.canon_open_custody(
  p_pet_id uuid, p_kind text, p_person uuid, p_org uuid,
  p_source text, p_source_id uuid, p_purpose text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if not public._acl_pet_holder(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.pet_custody_records (
    pet_id, custodian_kind, custodian_person_id, custodian_organization_id,
    source_domain, source_record_id, purpose
  ) values (p_pet_id, p_kind, p_person, p_org, p_source, p_source_id, p_purpose)
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_close_custody(p_custody_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.pet_custody_records
    set status = 'ENDED', ends_at = timezone('utc', now())
    where id = p_custody_id and status = 'ACTIVE';
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
  insert into public.vitacora_moments (pet_id, kind, title, body, created_by)
  values (p_pet_id, p_kind, p_title, p_body, auth.uid())
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
begin
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
language sql
security definer
set search_path = public
as $$
  update public.vitacora_integration_links
    set visible = false, hidden_at = timezone('utc', now())
    where id = p_link_id;
$$;

create or replace function public.canon_invite_guardian(p_minor uuid, p_adult uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_adult public.persons%rowtype;
begin
  select * into v_adult from public.persons where user_id = p_adult;
  if public.person_age_band(v_adult.birth_date) <> 'ADULT_18_PLUS'
     or v_adult.lifecycle_status <> 'ACTIVE' then
    raise exception 'GUARDIAN_ADULT_INVALID';
  end if;
  insert into public.guardian_relationships (
    minor_user_id, adult_user_id, requested_by, status
  ) values (p_minor, p_adult, auth.uid(), 'PENDING')
  returning id into v_id;
  insert into public.guardian_relationship_events (relationship_id, actor_user_id, event_type)
  values (v_id, auth.uid(), 'INVITED');
  return v_id;
end;
$$;

create or replace function public.canon_accept_guardian(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare v_rel public.guardian_relationships%rowtype;
begin
  select * into v_rel from public.guardian_relationships where id = p_id;
  if auth.uid() not in (v_rel.minor_user_id, v_rel.adult_user_id) then
    raise exception 'FORBIDDEN';
  end if;
  update public.guardian_relationships
    set status = 'ACTIVE', accepted_at = timezone('utc', now()), verification_method = 'ACCOUNT_CONFIRMED'
    where id = p_id;
  insert into public.guardian_relationship_events (relationship_id, actor_user_id, event_type)
  values (p_id, auth.uid(), 'ACCEPTED');
end;
$$;

create or replace function public.canon_public_pet(p_code text)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'public_code', p.public_code,
    'name', p.name,
    'species', p.species_code,
    'sex', p.sex,
    'locality_id', p.home_locality_id
  )
  from public.pets p
  where p.public_code = p_code
    and p.lifecycle_status = 'ACTIVE';
$$;

create or replace function public.canon_public_lost_found(p_code text)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'public_code', a.public_code,
    'kind', a.kind,
    'status', a.status,
    'locality_id', a.locality_id
  )
  from public.lost_found_alerts a
  where a.public_code = p_code
    and a.status = 'OPEN';
$$;

create or replace function public.canon_create_provider(
  p_kind text, p_person uuid, p_org uuid, p_name text, p_category text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  insert into public.service_providers (holder_kind, holder_person_id, holder_organization_id, display_name)
  values (p_kind, p_person, p_org, p_name)
  returning id into v_id;
  insert into public.service_offerings (provider_id, category_code, name)
  values (v_id, p_category, p_name);
  return v_id;
end;
$$;

create or replace function public.canon_create_booking(
  p_offering uuid, p_pet uuid, p_starts timestamptz, p_zone text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_provider uuid;
begin
  select provider_id into v_provider from public.service_offerings where id = p_offering;
  insert into public.bookings (
    offering_id, provider_id, pet_id, booked_by, starts_at, zone_id, status
  ) values (p_offering, v_provider, p_pet, auth.uid(), p_starts, p_zone, 'CONFIRMED')
  returning id into v_id;
  insert into public.booking_instruction_snapshots (booking_id, feeding, specials)
  values (v_id, 'snapshot-feeding', 'agreed-only');
  return v_id;
end;
$$;

create or replace function public.canon_daycare_check_in(p_booking uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_stay uuid;
  v_pet uuid;
  v_custody uuid;
  v_org uuid;
begin
  select b.pet_id, sp.holder_organization_id
    into v_pet, v_org
  from public.bookings b
  join public.service_providers sp on sp.id = b.provider_id
  where b.id = p_booking;
  v_custody := public.canon_open_custody(
    v_pet, coalesce(case when v_org is not null then 'ORGANIZATION' end, 'PERSON'),
    case when v_org is null then auth.uid() end,
    v_org, 'DAYCARE', p_booking, 'DAYCARE_STAY'
  );
  insert into public.daycare_stays (booking_id, pet_id, custody_id, checked_in_at, status)
  values (p_booking, v_pet, v_custody, timezone('utc', now()), 'IN_STAY')
  returning id into v_stay;
  insert into public.daycare_public_consents (stay_id, granted, granted_by)
  values (v_stay, false, auth.uid());
  update public.bookings set status = 'CHECKED_IN' where id = p_booking;
  return v_stay;
end;
$$;

create or replace function public.canon_daycare_event(p_stay uuid, p_type text, p_note text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  insert into public.daycare_care_events (stay_id, event_type, note, actor_user_id)
  values (p_stay, p_type, p_note, auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_daycare_checkout(p_stay uuid, p_save_vitacora boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare v_stay public.daycare_stays%rowtype;
begin
  select * into v_stay from public.daycare_stays where id = p_stay;
  update public.daycare_stays
    set status = 'COMPLETED', checked_out_at = timezone('utc', now())
    where id = p_stay;
  perform public.canon_close_custody(v_stay.custody_id);
  update public.bookings set status = 'CHECKED_OUT' where id = v_stay.booking_id;
  if p_save_vitacora then
    insert into public.vitacora_integration_links (pet_id, source_table, source_record_id, created_by)
    values (v_stay.pet_id, 'daycare_stays', p_stay, auth.uid())
    on conflict (pet_id, source_table, source_record_id) do nothing;
  end if;
end;
$$;

create or replace function public.canon_start_conversation(
  p_kind text, p_other_person uuid, p_org uuid, p_body text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_allow boolean;
begin
  if p_kind = 'PERSON' then
    select c.allow_unknown_dms into v_allow
    from public.person_contact_controls c where c.user_id = p_other_person;
    if v_allow is false and not exists (
      select 1 from public.friendships f
      where f.status = 'ACCEPTED'
        and ((f.requester_id = auth.uid() and f.addressee_id = p_other_person)
          or (f.addressee_id = auth.uid() and f.requester_id = p_other_person))
    ) then
      raise exception 'UNKNOWN_DM_RESTRICTED';
    end if;
  end if;
  insert into public.conversations (subject_kind, created_by)
  values (p_kind, auth.uid()) returning id into v_id;
  insert into public.conversation_participants (conversation_id, participant_kind, person_id)
  values (v_id, 'PERSON', auth.uid());
  if p_kind = 'PERSON' then
    insert into public.conversation_participants (conversation_id, participant_kind, person_id)
    values (v_id, 'PERSON', p_other_person);
  else
    insert into public.conversation_participants (conversation_id, participant_kind, organization_id)
    values (v_id, 'ORGANIZATION', p_org);
  end if;
  insert into public.messages (conversation_id, actor_user_id, body)
  values (v_id, auth.uid(), p_body);
  return v_id;
end;
$$;

create or replace function public.canon_register_media(
  p_bucket text, p_path text, p_mime text, p_size bigint
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  insert into public.media_assets (
    bucket, object_path, mime_type, byte_size, owner_kind, owner_person_id, created_by, lifecycle_status
  ) values (
    p_bucket, p_path, p_mime, p_size, 'PERSON', auth.uid(), auth.uid(), 'READY'
  ) returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_record_consent(
  p_subject uuid, p_document uuid, p_type text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  insert into public.legal_consent_events (
    subject_user_id, actor_user_id, document_id, event_type, source
  ) values (p_subject, auth.uid(), p_document, p_type, 'APP')
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_create_privacy_request(p_type text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  insert into public.privacy_requests (subject_user_id, requester_user_id, request_type)
  values (auth.uid(), auth.uid(), p_type)
  returning id into v_id;
  insert into public.privacy_request_events (request_id, actor_user_id, to_status)
  values (v_id, auth.uid(), 'OPEN');
  return v_id;
end;
$$;

create or replace function public.canon_create_professional()
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  insert into public.professional_profiles (person_id)
  values (auth.uid())
  on conflict (person_id) do update set active = true
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_record_vet_care(
  p_pet uuid, p_org uuid, p_summary text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_prof uuid;
  v_id uuid;
begin
  select id into v_prof from public.professional_profiles where person_id = auth.uid();
  if v_prof is null then raise exception 'NOT_A_VETERINARY_PROFESSIONAL'; end if;
  insert into public.veterinary_care_records (
    pet_id, actor_user_id, professional_profile_id, organization_id, summary, care_on
  ) values (p_pet, auth.uid(), v_prof, p_org, p_summary, current_date)
  returning id into v_id;
  return v_id;
end;
$$;

grant execute on function public.canon_public_pet(text) to anon, authenticated;
grant execute on function public.canon_public_lost_found(text) to anon, authenticated;
grant execute on function public.person_age_band(date, date) to authenticated;
grant execute on function public.person_is_under_13(date, date) to authenticated;

do $$
declare r record;
begin
  for r in
    select p.proname, pg_get_function_identity_arguments(p.oid) as args
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname like 'canon_%'
  loop
    execute format('grant execute on function public.%I(%s) to authenticated', r.proname, r.args);
  end loop;
end$$;
