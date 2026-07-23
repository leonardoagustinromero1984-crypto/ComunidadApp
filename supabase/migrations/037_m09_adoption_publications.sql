-- =============================================================================
-- LeoVer M09 — migración 037: publicaciones de adopción (bloque 1)
-- Forward-only sobre 001–036. NO reescribe migraciones anteriores.
-- LOCAL ONLY hasta apply remoto autorizado. Sin db push en esta etapa.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Catálogo de estados de publicación
-- ---------------------------------------------------------------------------
create or replace function public.m09_adoption_publication_statuses()
returns text[]
language sql
immutable
parallel safe
as $$
  select array['DRAFT', 'PUBLISHED', 'PAUSED', 'ADOPTED', 'CLOSED']::text[];
$$;

create or replace function public.m09_adoption_open_statuses()
returns text[]
language sql
immutable
parallel safe
as $$
  select array['DRAFT', 'PUBLISHED', 'PAUSED']::text[];
$$;

-- ---------------------------------------------------------------------------
-- 2. Evolución de public.adoptions (legacy → publicación ligada a pet)
-- ---------------------------------------------------------------------------
alter table public.adoptions
  add column if not exists pet_id uuid references public.pets (id) on delete restrict,
  add column if not exists title text,
  add column if not exists requirements text,
  add column if not exists location_text text,
  add column if not exists published_at timestamptz,
  add column if not exists publisher_organization_id uuid;

-- Backfill títulos / location_text desde columnas legacy
update public.adoptions
set title = coalesce(nullif(trim(title), ''), name)
where title is null or trim(title) = '';

update public.adoptions
set location_text = coalesce(nullif(trim(location_text), ''), location)
where location_text is null or trim(location_text) = '';

-- Migrar estados legacy → catálogo M09
update public.adoptions set status = 'PUBLISHED' where status = 'AVAILABLE';
update public.adoptions set status = 'PAUSED' where status = 'IN_PROCESS';
update public.adoptions
set published_at = coalesce(published_at, created_at)
where status = 'PUBLISHED' and published_at is null;

alter table public.adoptions
  alter column title set default '',
  alter column requirements set default '';

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'adoptions_status_m09_chk'
      and conrelid = 'public.adoptions'::regclass
  ) then
    alter table public.adoptions
      add constraint adoptions_status_m09_chk
      check (status = any (public.m09_adoption_publication_statuses()));
  end if;
end;
$$;

create index if not exists adoptions_pet_id_idx
  on public.adoptions (pet_id);

create index if not exists adoptions_status_idx
  on public.adoptions (status);

create index if not exists adoptions_published_at_idx
  on public.adoptions (published_at desc nulls last);

-- Una sola publicación "abierta" por mascota
create unique index if not exists adoptions_one_open_per_pet_uidx
  on public.adoptions (pet_id)
  where pet_id is not null
    and status = any (public.m09_adoption_open_statuses());

-- ---------------------------------------------------------------------------
-- 3. Helpers de autorización (reutilizan M08)
-- ---------------------------------------------------------------------------
create or replace function public._m09_actor_can_manage_pet(p_pet_id uuid, p_actor uuid default auth.uid())
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_caps text[];
begin
  if p_actor is null then
    return false;
  end if;
  if not public.m08_actor_can_read_pet(p_pet_id, p_actor) then
    return false;
  end if;
  v_caps := public._m08_actor_effective_capabilities(p_pet_id, p_actor);
  return ('pet.update' = any (v_caps)) or ('pet.manage_responsibilities' = any (v_caps));
end;
$$;

create or replace function public._m09_require_authenticated()
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := auth.uid();
begin
  if v_actor is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  return v_actor;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RLS: lectura pública solo PUBLISHED; dueño/gestor ve las propias
-- ---------------------------------------------------------------------------
alter table public.adoptions enable row level security;

drop policy if exists adoptions_select_authenticated on public.adoptions;
drop policy if exists adoptions_insert_own on public.adoptions;
drop policy if exists adoptions_update_own on public.adoptions;
drop policy if exists adoptions_delete_own on public.adoptions;
drop policy if exists adoptions_select_m09 on public.adoptions;
drop policy if exists adoptions_insert_m09 on public.adoptions;
drop policy if exists adoptions_update_m09 on public.adoptions;
drop policy if exists adoptions_delete_m09 on public.adoptions;

create policy adoptions_select_m09
  on public.adoptions for select to authenticated
  using (
    status = 'PUBLISHED'
    or publisher_id = auth.uid()
    or (
      pet_id is not null
      and public.m08_actor_can_read_pet(pet_id, auth.uid())
    )
  );

-- Escrituras directas denegadas: canalizar por RPC m09_*
create policy adoptions_insert_m09
  on public.adoptions for insert to authenticated
  with check (false);

create policy adoptions_update_m09
  on public.adoptions for update to authenticated
  using (false);

create policy adoptions_delete_m09
  on public.adoptions for delete to authenticated
  using (false);

-- ---------------------------------------------------------------------------
-- 5. RPCs de publicación
-- ---------------------------------------------------------------------------
create or replace function public.m09_create_adoption_publication(
  p_pet_id uuid,
  p_title text,
  p_description text,
  p_requirements text default '',
  p_location_text text default '',
  p_publish boolean default false
)
returns public.adoptions
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_pet public.pets;
  v_row public.adoptions;
  v_status text;
  v_name text;
begin
  if p_pet_id is null then
    raise exception 'PET_NOT_FOUND';
  end if;
  select * into v_pet from public.pets p where p.id = p_pet_id;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status = 'DECEASED' then
    raise exception 'PET_NOT_ADOPTABLE';
  end if;
  if v_pet.status = 'ARCHIVED' then
    raise exception 'PET_NOT_ADOPTABLE';
  end if;
  if v_pet.status <> 'ACTIVE' then
    raise exception 'PET_NOT_ADOPTABLE';
  end if;
  if not public._m09_actor_can_manage_pet(p_pet_id, v_actor) then
    raise exception 'FORBIDDEN';
  end if;
  if exists (
    select 1 from public.adoptions a
    where a.pet_id = p_pet_id
      and a.status = any (public.m09_adoption_open_statuses())
  ) then
    raise exception 'ADOPTION_ALREADY_EXISTS';
  end if;
  if coalesce(trim(p_title), '') = '' then
    raise exception 'ADOPTION_TITLE_REQUIRED';
  end if;
  if coalesce(trim(p_description), '') = '' then
    raise exception 'ADOPTION_DESCRIPTION_REQUIRED';
  end if;

  v_status := case when p_publish then 'PUBLISHED' else 'DRAFT' end;
  select coalesce(nullif(trim(u.display_name), ''), u.name, 'Usuario') into v_name
  from public.users u where u.id = v_actor;

  insert into public.adoptions (
    publisher_id, publisher_name, shelter_id, pet_id,
    name, title, description, requirements, location, location_text,
    photo_url, species, sex, age_years, age_months, size,
    status, published_at, created_at, updated_at
  ) values (
    v_actor,
    coalesce(v_name, 'Usuario'),
    null,
    p_pet_id,
    v_pet.name,
    trim(p_title),
    trim(p_description),
    coalesce(p_requirements, ''),
    coalesce(nullif(trim(p_location_text), ''), ''),
    coalesce(nullif(trim(p_location_text), ''), ''),
    v_pet.photo_url,
    v_pet.species,
    v_pet.sex,
    v_pet.age_years,
    v_pet.age_months,
    v_pet.size,
    v_status,
    case when p_publish then timezone('utc', now()) else null end,
    timezone('utc', now()),
    timezone('utc', now())
  )
  returning * into v_row;

  return v_row;
exception
  when unique_violation then
    raise exception 'ADOPTION_ALREADY_EXISTS';
end;
$$;

create or replace function public.m09_update_adoption_publication(
  p_adoption_id uuid,
  p_title text,
  p_description text,
  p_requirements text default '',
  p_location_text text default ''
)
returns public.adoptions
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoptions;
begin
  select * into v_row from public.adoptions a where a.id = p_adoption_id;
  if not found then
    raise exception 'ADOPTION_NOT_FOUND';
  end if;
  if v_row.status in ('CLOSED', 'ADOPTED') then
    raise exception 'ADOPTION_NOT_EDITABLE';
  end if;
  if v_row.publisher_id <> v_actor
     and (v_row.pet_id is null or not public._m09_actor_can_manage_pet(v_row.pet_id, v_actor)) then
    raise exception 'FORBIDDEN';
  end if;
  if coalesce(trim(p_title), '') = '' or coalesce(trim(p_description), '') = '' then
    raise exception 'ADOPTION_NOT_EDITABLE';
  end if;

  update public.adoptions a set
    title = trim(p_title),
    name = coalesce(a.name, trim(p_title)),
    description = trim(p_description),
    requirements = coalesce(p_requirements, ''),
    location_text = coalesce(p_location_text, ''),
    location = coalesce(nullif(trim(p_location_text), ''), a.location),
    updated_at = timezone('utc', now())
  where a.id = p_adoption_id
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public.m09_set_adoption_status(
  p_adoption_id uuid,
  p_status text
)
returns public.adoptions
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoptions;
  v_target text := upper(trim(p_status));
begin
  if v_target is null or not (v_target = any (public.m09_adoption_publication_statuses())) then
    raise exception 'ADOPTION_STATUS_INVALID';
  end if;
  select * into v_row from public.adoptions a where a.id = p_adoption_id;
  if not found then
    raise exception 'ADOPTION_NOT_FOUND';
  end if;
  if v_row.publisher_id <> v_actor
     and (v_row.pet_id is null or not public._m09_actor_can_manage_pet(v_row.pet_id, v_actor)) then
    raise exception 'FORBIDDEN';
  end if;

  if v_row.status = v_target then
    return v_row; -- idempotente
  end if;

  if v_row.status in ('CLOSED', 'ADOPTED') and v_target <> v_row.status then
    raise exception 'ADOPTION_NOT_EDITABLE';
  end if;

  if v_target = 'PAUSED' and v_row.status <> 'PUBLISHED' then
    raise exception 'ADOPTION_NOT_EDITABLE';
  end if;
  if v_target = 'PUBLISHED' and v_row.status not in ('DRAFT', 'PAUSED') then
    raise exception 'ADOPTION_NOT_EDITABLE';
  end if;
  if v_target = 'CLOSED' and v_row.status in ('ADOPTED') then
    raise exception 'ADOPTION_ALREADY_ADOPTED';
  end if;
  if v_target = 'ADOPTED' then
    raise exception 'ADOPTION_USE_MARK_ADOPTED';
  end if;

  update public.adoptions a set
    status = v_target,
    published_at = case
      when v_target = 'PUBLISHED' then coalesce(a.published_at, timezone('utc', now()))
      else a.published_at
    end,
    updated_at = timezone('utc', now())
  where a.id = p_adoption_id
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public.m09_mark_adoption_adopted(p_adoption_id uuid)
returns public.adoptions
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoptions;
  v_pet public.pets;
  v_prev text;
begin
  select * into v_row from public.adoptions a where a.id = p_adoption_id;
  if not found then
    raise exception 'ADOPTION_NOT_FOUND';
  end if;
  if v_row.status = 'ADOPTED' then
    return v_row; -- idempotente
  end if;
  if v_row.status = 'CLOSED' then
    raise exception 'ADOPTION_ALREADY_CLOSED';
  end if;
  if v_row.publisher_id <> v_actor
     and (v_row.pet_id is null or not public._m09_actor_can_manage_pet(v_row.pet_id, v_actor)) then
    raise exception 'FORBIDDEN';
  end if;

  update public.adoptions a set
    status = 'ADOPTED',
    updated_at = timezone('utc', now())
  where a.id = p_adoption_id
  returning * into v_row;

  if v_row.pet_id is not null then
    select * into v_pet from public.pets p where p.id = v_row.pet_id;
    if found and v_pet.status = 'ACTIVE' then
      v_prev := v_pet.status;
      update public.pets p set
        status = 'ARCHIVED',
        archived_at = timezone('utc', now()),
        updated_at = timezone('utc', now())
      where p.id = v_row.pet_id;
      insert into public.pet_status_history (
        pet_id, previous_status, new_status, reason, changed_by, changed_at
      ) values (
        v_row.pet_id, v_prev, 'ARCHIVED', 'ADOPTED', v_actor, timezone('utc', now())
      );
    end if;
  end if;

  return v_row;
end;
$$;

create or replace function public.m09_list_published_adoptions()
returns setof public.adoptions
language sql
stable
security definer
set search_path = public
as $$
  select a.*
  from public.adoptions a
  where a.status = 'PUBLISHED'
  order by coalesce(a.published_at, a.created_at) desc;
$$;

create or replace function public.m09_list_my_adoptions()
returns setof public.adoptions
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
begin
  return query
  select a.*
  from public.adoptions a
  where a.publisher_id = v_actor
     or (
       a.pet_id is not null
       and public._m09_actor_can_manage_pet(a.pet_id, v_actor)
     )
  order by a.updated_at desc;
end;
$$;

create or replace function public.m09_get_adoption(p_adoption_id uuid)
returns public.adoptions
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := auth.uid();
  v_row public.adoptions;
begin
  select * into v_row from public.adoptions a where a.id = p_adoption_id;
  if not found then
    raise exception 'ADOPTION_NOT_FOUND';
  end if;
  if v_row.status = 'PUBLISHED' then
    return v_row;
  end if;
  if v_actor is null then
    raise exception 'FORBIDDEN';
  end if;
  if v_row.publisher_id = v_actor
     or (v_row.pet_id is not null and public.m08_actor_can_read_pet(v_row.pet_id, v_actor)) then
    return v_row;
  end if;
  raise exception 'FORBIDDEN';
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Grants
-- ---------------------------------------------------------------------------
revoke all on function public.m09_adoption_publication_statuses() from public;
revoke all on function public.m09_adoption_open_statuses() from public;
revoke all on function public._m09_actor_can_manage_pet(uuid, uuid) from public;
revoke all on function public._m09_require_authenticated() from public;
revoke all on function public.m09_create_adoption_publication(uuid, text, text, text, text, boolean) from public;
revoke all on function public.m09_update_adoption_publication(uuid, text, text, text, text) from public;
revoke all on function public.m09_set_adoption_status(uuid, text) from public;
revoke all on function public.m09_mark_adoption_adopted(uuid) from public;
revoke all on function public.m09_list_published_adoptions() from public;
revoke all on function public.m09_list_my_adoptions() from public;
revoke all on function public.m09_get_adoption(uuid) from public;

grant execute on function public.m09_adoption_publication_statuses() to authenticated;
grant execute on function public.m09_adoption_open_statuses() to authenticated;
grant execute on function public.m09_create_adoption_publication(uuid, text, text, text, text, boolean) to authenticated;
grant execute on function public.m09_update_adoption_publication(uuid, text, text, text, text) to authenticated;
grant execute on function public.m09_set_adoption_status(uuid, text) to authenticated;
grant execute on function public.m09_mark_adoption_adopted(uuid) to authenticated;
grant execute on function public.m09_list_published_adoptions() to authenticated;
grant execute on function public.m09_list_my_adoptions() to authenticated;
grant execute on function public.m09_get_adoption(uuid) to authenticated;

comment on function public.m09_create_adoption_publication(uuid, text, text, text, text, boolean) is
  'M09: create adoption publication linked to pet; open-unique per pet; draft or publish.';
comment on function public.m09_mark_adoption_adopted(uuid) is
  'M09: mark publication ADOPTED and archive linked ACTIVE pet with history reason ADOPTED.';

commit;
