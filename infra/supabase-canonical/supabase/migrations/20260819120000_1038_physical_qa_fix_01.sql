-- LeoVer Canonical
-- Logical migration: 1038
-- Physical QA FIX-01: person avatar write, pet-friendly catalog, foster capability,
-- PERSON cannot publish adoptions without rescue/org authority.

-- ---------------------------------------------------------------------------
-- canon_set_person_avatar — persist persons.avatar_asset_id. NULL clears.
-- ---------------------------------------------------------------------------

create or replace function public.canon_set_person_avatar(
  p_media_asset_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_asset public.media_assets%rowtype;
  v_prev uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (select 1 from public.persons where user_id = auth.uid()) then
    raise exception 'PERSON_NOT_FOUND';
  end if;

  select avatar_asset_id into v_prev from public.persons where user_id = auth.uid();

  if p_media_asset_id is not null then
    select * into v_asset from public.media_assets where id = p_media_asset_id;
    if not found then raise exception 'MEDIA_ASSET_NOT_FOUND'; end if;
    if v_asset.lifecycle_status not in ('READY', 'UPLOADING') then
      raise exception 'MEDIA_ASSET_NOT_READY';
    end if;
    if v_asset.owner_kind <> 'PERSON' or v_asset.owner_person_id <> auth.uid() then
      raise exception 'MEDIA_ASSET_NOT_OWNED';
    end if;
    if v_asset.mime_type not like 'image/%' then
      raise exception 'MEDIA_ASSET_NOT_IMAGE';
    end if;
    if v_asset.metadata ? 'signed_url' or v_asset.metadata ? 'signedUrl' then
      raise exception 'SIGNED_URL_FORBIDDEN';
    end if;
  end if;

  update public.persons
    set avatar_asset_id = p_media_asset_id
    where user_id = auth.uid();

  if p_media_asset_id is not null then
    insert into public.media_asset_links (asset_id, owner_table, owner_id, purpose)
    select p_media_asset_id, 'persons', auth.uid(), 'USER_AVATAR'
    where not exists (
      select 1 from public.media_asset_links
      where asset_id = p_media_asset_id
        and owner_table = 'persons'
        and owner_id = auth.uid()
        and purpose = 'USER_AVATAR'
    );
  end if;

  if v_prev is not null and v_prev is distinct from p_media_asset_id then
    update public.media_assets
      set lifecycle_status = 'ARCHIVED',
          archived_at = timezone('utc', now()),
          metadata = metadata || jsonb_build_object('orphan_candidate', true)
      where id = v_prev
        and not exists (select 1 from public.pets p where p.avatar_asset_id = v_prev)
        and not exists (select 1 from public.persons per where per.avatar_asset_id = v_prev);
  end if;

  perform public.canon_audit(
    'person.set_avatar', 'persons', auth.uid(),
    jsonb_build_object('asset', p_media_asset_id, 'previous', v_prev)
  );
  return auth.uid();
end;
$$;

grant execute on function public.canon_set_person_avatar(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Pet-friendly venue catalog (second-level business kind)
-- ---------------------------------------------------------------------------

insert into public.service_categories (code, name, sort_key) values
  ('PET_FRIENDLY', 'Lugar pet friendly', 20),
  ('GROOMING', 'Peluquería', 7),
  ('SHOP', 'Tienda', 8)
on conflict (code) do nothing;

create table if not exists public.pet_friendly_venue_subtypes (
  code text primary key,
  name text not null,
  sort_key int not null
);

comment on table public.pet_friendly_venue_subtypes is
  'Catalog of pet-friendly venue subtypes. Not a first-level actor.';

alter table public.pet_friendly_venue_subtypes enable row level security;

drop policy if exists pet_friendly_venue_subtypes_read on public.pet_friendly_venue_subtypes;
create policy pet_friendly_venue_subtypes_read
  on public.pet_friendly_venue_subtypes
  for select
  to authenticated, anon
  using (true);

insert into public.pet_friendly_venue_subtypes (code, name, sort_key) values
  ('HOTEL', 'Hotel / Alojamiento', 1),
  ('CABIN', 'Cabaña / Complejo', 2),
  ('RESTAURANT', 'Restaurante', 3),
  ('CAFE_BAR', 'Café / Bar', 4),
  ('CAMPING', 'Camping', 5),
  ('OTHER', 'Otro', 99)
on conflict (code) do nothing;

-- ---------------------------------------------------------------------------
-- Foster as PERSON capability (additive; RESCUER already existed)
-- ---------------------------------------------------------------------------

alter table public.person_capabilities drop constraint if exists person_capabilities_capability_check;
alter table public.person_capabilities
  add constraint person_capabilities_capability_check
  check (capability in ('RESCUER', 'FOSTER'));

create or replace function public.canon_set_person_capability(
  p_capability text,
  p_active boolean default true
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if p_capability not in ('RESCUER', 'FOSTER') then
    raise exception 'PERSON_CAPABILITY_UNKNOWN';
  end if;
  if not exists (select 1 from public.persons where user_id = auth.uid()) then
    raise exception 'PERSON_NOT_FOUND';
  end if;

  insert into public.person_capabilities (user_id, capability, active, deactivated_at, updated_at)
  values (
    auth.uid(),
    p_capability,
    coalesce(p_active, true),
    case when coalesce(p_active, true) then null else timezone('utc', now()) end,
    timezone('utc', now())
  )
  on conflict (user_id, capability) do update
    set active = excluded.active,
        deactivated_at = excluded.deactivated_at,
        updated_at = timezone('utc', now());

  return coalesce(p_active, true);
end;
$$;

-- When a foster profile is upserted, enable FOSTER capability.
create or replace function public._qa_fix01_sync_foster_capability()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.person_capabilities (user_id, capability, active, deactivated_at, updated_at)
  values (new.user_id, 'FOSTER', coalesce(new.active, true), null, timezone('utc', now()))
  on conflict (user_id, capability) do update
    set active = coalesce(new.active, true),
        deactivated_at = case when coalesce(new.active, true) then null else timezone('utc', now()) end,
        updated_at = timezone('utc', now());
  return new;
end;
$$;

drop trigger if exists trg_foster_profile_capability on public.foster_profiles;
create trigger trg_foster_profile_capability
  after insert or update on public.foster_profiles
  for each row execute function public._qa_fix01_sync_foster_capability();

-- ---------------------------------------------------------------------------
-- PERSON cannot publish adoptions unless RESCUER or shelter organization
-- ---------------------------------------------------------------------------

create or replace function public.canon_create_adoption(
  p_pet_id uuid,
  p_organization_id uuid default null,
  p_note text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
declare v_rescuer boolean;
declare v_org_ok boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_edit_pet(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;

  select exists (
    select 1 from public.person_capabilities
    where user_id = auth.uid() and capability = 'RESCUER' and active
  ) into v_rescuer;

  v_org_ok := false;
  if p_organization_id is not null then
    v_org_ok := public._acl_org_member(auth.uid(), p_organization_id);
  end if;

  if not v_rescuer and not v_org_ok then
    raise exception 'ADOPTION_PUBLISH_FORBIDDEN';
  end if;

  if exists (
    select 1 from public.adoption_publications
    where pet_id = p_pet_id and status = 'OPEN'
  ) then
    raise exception 'ADOPTION_ALREADY_EXISTS';
  end if;
  insert into public.adoption_publications (pet_id, published_by, organization_id, note)
  values (p_pet_id, auth.uid(), p_organization_id, p_note)
  returning id into v_id;
  perform public.canon_audit('adoption.create', 'adoption_publications', v_id, '{}'::jsonb);
  return v_id;
end;
$$;
