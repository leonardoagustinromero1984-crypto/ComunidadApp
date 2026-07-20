-- =============================================================================
-- LeoVer M08 — migración 035: pets responsibilities, transfers, RLS y RPC
-- =============================================================================
-- Estado: VALIDADA LOCALMENTE (Etapa 3B). STAGING NO AUTORIZADO.
-- REQUIERE ETAPA 4 — adaptar SupabasePetRepository antes de aplicar remoto.
-- Motivo: el cliente Android legacy escribe public.pets/owner_id directamente;
--         035 revoca esas escrituras y las canaliza por RPC m08_*.
-- NO aplicar con db push --linked. NO ejecutar en Dashboard remoto.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Precondiciones
-- ---------------------------------------------------------------------------
do $$
begin
  if to_regclass('public.pets') is null then
    raise exception 'M08_035_PRECHECK: public.pets missing';
  end if;
  if to_regclass('public.permissions') is null then
    raise exception 'M08_035_PRECHECK: public.permissions missing (M02)';
  end if;
  if to_regclass('public.file_assets') is null then
    raise exception 'M08_035_PRECHECK: public.file_assets missing (M05)';
  end if;
  if to_regclass('public.organizations') is null then
    raise exception 'M08_035_PRECHECK: public.organizations missing (M03)';
  end if;
  if to_regclass('public.observability_event_catalog') is null then
    raise exception 'M08_035_PRECHECK: observability_event_catalog missing (M07)';
  end if;
  if exists (
    select 1 from information_schema.tables
    where table_schema = 'public' and table_name = 'pet_responsibilities'
  ) then
    raise exception 'M08_035_PRECHECK: pet_responsibilities already exists';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2. Helpers puros (allowlists + normalización)
-- ---------------------------------------------------------------------------
create or replace function public.m08_pet_lifecycle_statuses()
returns text[]
language sql
immutable
parallel safe
as $$
  select array['ACTIVE', 'DECEASED', 'ARCHIVED']::text[];
$$;

create or replace function public.m08_pet_responsibility_roles()
returns text[]
language sql
immutable
parallel safe
as $$
  select array['PRINCIPAL', 'CO_RESPONSIBLE', 'TEMPORARY_CUSTODIAN']::text[];
$$;

create or replace function public.m08_pet_link_statuses()
returns text[]
language sql
immutable
parallel safe
as $$
  select array['ACTIVE', 'PENDING_ACCEPTANCE', 'REVOKED', 'EXPIRED', 'SUPERSEDED']::text[];
$$;

create or replace function public.m08_pet_transfer_statuses()
returns text[]
language sql
immutable
parallel safe
as $$
  select array['PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED']::text[];
$$;

create or replace function public.m08_pet_capability_codes()
returns text[]
language sql
immutable
parallel safe
as $$
  select array[
    'pet.read',
    'pet.create',
    'pet.update',
    'pet.manage_responsibilities',
    'pet.manage_authorizations',
    'pet.initiate_transfer',
    'pet.accept_transfer',
    'pet.cancel_transfer',
    'pet.mark_deceased',
    'pet.archive',
    'pet.restore',
    'pet.manage_media',
    'pet.view_history',
    'pet.manage_health'
  ]::text[];
$$;

create or replace function public.m08_pet_authorization_grantable_capabilities()
returns text[]
language sql
immutable
parallel safe
as $$
  -- Ownership / lifecycle caps no se otorgan vía autorización (dominio Etapa 2).
  select array(
    select c
    from unnest(public.m08_pet_capability_codes()) as c
    where c not in (
      'pet.initiate_transfer',
      'pet.mark_deceased',
      'pet.archive',
      'pet.manage_responsibilities'
    )
  );
$$;

create or replace function public.m08_normalize_microchip(p_raw text)
returns text
language sql
immutable
parallel safe
as $$
  select nullif(
    upper(regexp_replace(trim(coalesce(p_raw, '')), '[\s\-_]+', '', 'g')),
    ''
  );
$$;

create or replace function public.m08_capabilities_are_allowed(p_caps text[])
returns boolean
language sql
immutable
parallel safe
as $$
  select p_caps is not null
     and cardinality(p_caps) > 0
     and p_caps <@ public.m08_pet_capability_codes();
$$;

create or replace function public.m08_capabilities_are_grantable(p_caps text[])
returns boolean
language sql
immutable
parallel safe
as $$
  select public.m08_capabilities_are_allowed(p_caps)
     and p_caps <@ public.m08_pet_authorization_grantable_capabilities();
$$;

-- ---------------------------------------------------------------------------
-- 3. ALTER public.pets
-- ---------------------------------------------------------------------------
alter table public.pets
  add column if not exists status text not null default 'ACTIVE',
  add column if not exists deceased_at timestamptz null,
  add column if not exists archived_at timestamptz null,
  add column if not exists microchip_normalized text null,
  add column if not exists avatar_file_asset_id uuid null;

alter table public.pets
  drop constraint if exists pets_status_allowed;
alter table public.pets
  add constraint pets_status_allowed
  check (status = any (public.m08_pet_lifecycle_statuses()));

alter table public.pets
  drop constraint if exists pets_deceased_ts_chk;
alter table public.pets
  add constraint pets_deceased_ts_chk
  check (
    (status = 'DECEASED' and deceased_at is not null)
    or (status <> 'DECEASED' and deceased_at is null)
  );

alter table public.pets
  drop constraint if exists pets_archived_ts_chk;
alter table public.pets
  add constraint pets_archived_ts_chk
  check (
    (status = 'ARCHIVED' and archived_at is not null)
    or (status <> 'ARCHIVED')
  );

alter table public.pets
  drop constraint if exists pets_active_no_archived_at_chk;
alter table public.pets
  add constraint pets_active_no_archived_at_chk
  check (status <> 'ACTIVE' or archived_at is null);

-- avatar FK (M05)
do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'pets_avatar_file_asset_id_fkey'
  ) then
    alter table public.pets
      add constraint pets_avatar_file_asset_id_fkey
      foreign key (avatar_file_asset_id)
      references public.file_assets (id)
      on delete set null;
  end if;
end;
$$;

-- owner_id nullable + FK ON DELETE SET NULL (proyección legacy)
alter table public.pets alter column owner_id drop not null;

do $$
declare
  v_con text;
begin
  select c.conname into v_con
  from pg_constraint c
  join pg_class t on t.oid = c.conrelid
  join pg_namespace n on n.oid = t.relnamespace
  where n.nspname = 'public'
    and t.relname = 'pets'
    and c.contype = 'f'
    and pg_get_constraintdef(c.oid) ilike '%owner_id%';

  if v_con is not null then
    execute format('alter table public.pets drop constraint %I', v_con);
  end if;

  alter table public.pets
    add constraint pets_owner_id_fkey
    foreign key (owner_id)
    references public.users (id)
    on delete set null;
end;
$$;

create index if not exists pets_status_created_at_idx
  on public.pets (status, created_at desc);

create index if not exists pets_microchip_normalized_idx
  on public.pets (microchip_normalized)
  where microchip_normalized is not null;

create index if not exists pets_avatar_file_asset_id_idx
  on public.pets (avatar_file_asset_id)
  where avatar_file_asset_id is not null;

comment on column public.pets.owner_id is
  'M08 legacy projection of PRINCIPAL person_id; null when principal is organization-only. Updated only via m08_* SECURITY DEFINER RPCs. Direct client writes revoked.';
comment on column public.pets.status is
  'M08 lifecycle: ACTIVE | DECEASED | ARCHIVED';
comment on column public.pets.microchip_normalized is
  'Normalized microchip for soft-unique ACTIVE index; source remains microchip_id';
comment on column public.pets.photo_url is
  'Legacy URL retained; canonical avatar is avatar_file_asset_id (M05)';

-- ---------------------------------------------------------------------------
-- 4. Tablas nuevas
-- ---------------------------------------------------------------------------
create table public.pet_responsibilities (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  role_code text not null,
  person_id uuid null references public.users (id) on delete restrict,
  organization_id uuid null references public.organizations (id) on delete restrict,
  status text not null default 'ACTIVE',
  starts_at timestamptz not null,
  ends_at timestamptz null,
  revoked_at timestamptz null,
  revoked_by uuid null references public.users (id) on delete set null,
  created_by uuid not null references public.users (id) on delete restrict,
  created_at timestamptz not null default timezone('utc', now()),
  accepted_at timestamptz null,
  reason text null,
  constraint pet_responsibilities_role_allowed
    check (role_code = any (public.m08_pet_responsibility_roles())),
  constraint pet_responsibilities_status_allowed
    check (status = any (public.m08_pet_link_statuses())),
  constraint pet_responsibilities_actor_xor
    check (
      (person_id is not null and organization_id is null)
      or (person_id is null and organization_id is not null)
    ),
  constraint pet_responsibilities_temp_ends_chk
    check (
      role_code <> 'TEMPORARY_CUSTODIAN'
      or (ends_at is not null and ends_at > starts_at)
    ),
  constraint pet_responsibilities_ends_after_starts_chk
    check (ends_at is null or ends_at >= starts_at),
  constraint pet_responsibilities_revoked_coherence_chk
    check (
      (status = 'REVOKED' and revoked_at is not null)
      or (status <> 'REVOKED')
    )
);

create table public.pet_authorizations (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  person_id uuid not null references public.users (id) on delete restrict,
  granted_by uuid not null references public.users (id) on delete restrict,
  capabilities text[] not null,
  status text not null default 'ACTIVE',
  valid_from timestamptz not null,
  valid_until timestamptz null,
  revoked_at timestamptz null,
  revoked_by uuid null references public.users (id) on delete set null,
  created_at timestamptz not null default timezone('utc', now()),
  accepted_at timestamptz null,
  constraint pet_authorizations_status_allowed
    check (status = any (public.m08_pet_link_statuses())),
  constraint pet_authorizations_caps_not_empty
    check (cardinality(capabilities) > 0),
  constraint pet_authorizations_caps_grantable
    check (public.m08_capabilities_are_grantable(capabilities)),
  constraint pet_authorizations_validity_chk
    check (valid_until is null or valid_until > valid_from),
  constraint pet_authorizations_revoked_coherence_chk
    check (
      (status = 'REVOKED' and revoked_at is not null)
      or (status <> 'REVOKED')
    )
);

create table public.pet_transfers (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  from_person_id uuid null references public.users (id) on delete restrict,
  from_organization_id uuid null references public.organizations (id) on delete restrict,
  to_person_id uuid null references public.users (id) on delete restrict,
  to_organization_id uuid null references public.organizations (id) on delete restrict,
  status text not null default 'PENDING',
  requested_by uuid not null references public.users (id) on delete restrict,
  requested_at timestamptz not null default timezone('utc', now()),
  expires_at timestamptz not null,
  responded_at timestamptz null,
  responded_by uuid null references public.users (id) on delete set null,
  cancelled_at timestamptz null,
  cancellation_reason text null,
  correlation_id text null,
  created_at timestamptz not null default timezone('utc', now()),
  constraint pet_transfers_status_allowed
    check (status = any (public.m08_pet_transfer_statuses())),
  constraint pet_transfers_from_xor
    check (
      (from_person_id is not null and from_organization_id is null)
      or (from_person_id is null and from_organization_id is not null)
    ),
  constraint pet_transfers_to_xor
    check (
      (to_person_id is not null and to_organization_id is null)
      or (to_person_id is null and to_organization_id is not null)
    ),
  constraint pet_transfers_from_ne_to
    check (
      coalesce(from_person_id::text, '') <> coalesce(to_person_id::text, '')
      or coalesce(from_organization_id::text, '') <> coalesce(to_organization_id::text, '')
    ),
  constraint pet_transfers_expires_after_requested
    check (expires_at > requested_at)
);

create table public.pet_status_history (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  previous_status text null,
  new_status text not null,
  changed_by uuid not null references public.users (id) on delete restrict,
  changed_at timestamptz not null default timezone('utc', now()),
  reason text null,
  metadata jsonb not null default '{}'::jsonb,
  correlation_id text null,
  constraint pet_status_history_new_status_allowed
    check (new_status = any (public.m08_pet_lifecycle_statuses())),
  constraint pet_status_history_prev_status_allowed
    check (
      previous_status is null
      or previous_status = any (public.m08_pet_lifecycle_statuses())
    )
);

-- ---------------------------------------------------------------------------
-- 5–6. Constraints e índices (unicidad parcial)
-- ---------------------------------------------------------------------------
create unique index pet_responsibilities_one_principal_active_uniq
  on public.pet_responsibilities (pet_id)
  where role_code = 'PRINCIPAL' and status = 'ACTIVE';

create unique index pet_responsibilities_person_role_active_uniq
  on public.pet_responsibilities (pet_id, role_code, person_id)
  where status = 'ACTIVE' and person_id is not null;

create unique index pet_responsibilities_org_role_active_uniq
  on public.pet_responsibilities (pet_id, role_code, organization_id)
  where status = 'ACTIVE' and organization_id is not null;

create index pet_responsibilities_pet_created_idx
  on public.pet_responsibilities (pet_id, created_at desc);

create index pet_responsibilities_person_active_idx
  on public.pet_responsibilities (person_id)
  where status = 'ACTIVE' and person_id is not null;

create index pet_responsibilities_org_active_idx
  on public.pet_responsibilities (organization_id)
  where status = 'ACTIVE' and organization_id is not null;

create unique index pet_authorizations_one_active_per_person_uniq
  on public.pet_authorizations (pet_id, person_id)
  where status = 'ACTIVE';

create index pet_authorizations_pet_created_idx
  on public.pet_authorizations (pet_id, created_at desc);

create unique index pet_transfers_one_pending_uniq
  on public.pet_transfers (pet_id)
  where status = 'PENDING';

create index pet_transfers_status_expires_idx
  on public.pet_transfers (status, expires_at);

create index pet_status_history_pet_changed_idx
  on public.pet_status_history (pet_id, changed_at desc);

-- ---------------------------------------------------------------------------
-- 7. Helpers de seguridad / proyección (internos y de lectura)
-- ---------------------------------------------------------------------------
create or replace function public._m08_require_authenticated()
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  return v_uid;
end;
$$;

create or replace function public._m08_role_default_capabilities(p_role text)
returns text[]
language sql
immutable
parallel safe
as $$
  select case upper(trim(coalesce(p_role, '')))
    when 'PRINCIPAL' then array[
      'pet.read','pet.create','pet.update','pet.manage_responsibilities',
      'pet.manage_authorizations','pet.initiate_transfer','pet.cancel_transfer',
      'pet.mark_deceased','pet.archive','pet.restore','pet.manage_media',
      'pet.view_history','pet.manage_health'
    ]::text[]
    when 'CO_RESPONSIBLE' then array[
      'pet.read','pet.update','pet.manage_media','pet.manage_health','pet.view_history'
    ]::text[]
    when 'TEMPORARY_CUSTODIAN' then array[
      'pet.read','pet.update','pet.manage_health','pet.view_history'
    ]::text[]
    else array[]::text[]
  end;
$$;

create or replace function public._m08_write_status_history(
  p_pet_id uuid,
  p_previous text,
  p_new text,
  p_changed_by uuid,
  p_reason text default null,
  p_metadata jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  insert into public.pet_status_history (
    pet_id, previous_status, new_status, changed_by, changed_at, reason, metadata
  ) values (
    p_pet_id, p_previous, p_new, p_changed_by, timezone('utc', now()), p_reason,
    coalesce(p_metadata, '{}'::jsonb)
  )
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public._m08_sync_owner_id_from_principal(p_pet_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_person uuid;
begin
  select r.person_id into v_person
  from public.pet_responsibilities r
  where r.pet_id = p_pet_id
    and r.role_code = 'PRINCIPAL'
    and r.status = 'ACTIVE'
  limit 1;

  update public.pets p
  set owner_id = v_person,
      updated_at = timezone('utc', now())
  where p.id = p_pet_id
    and p.owner_id is distinct from v_person;
end;
$$;

create or replace function public.m08_current_principal(p_pet_id uuid)
returns table (
  responsibility_id uuid,
  person_id uuid,
  organization_id uuid
)
language sql
stable
security definer
set search_path = public
as $$
  select r.id, r.person_id, r.organization_id
  from public.pet_responsibilities r
  where r.pet_id = p_pet_id
    and r.role_code = 'PRINCIPAL'
    and r.status = 'ACTIVE'
  limit 1;
$$;

create or replace function public.m08_actor_has_active_responsibility(
  p_pet_id uuid,
  p_actor uuid default auth.uid()
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.pet_responsibilities r
    where r.pet_id = p_pet_id
      and r.status = 'ACTIVE'
      and r.person_id = p_actor
      and (r.ends_at is null or r.ends_at > timezone('utc', now()))
  )
  or exists (
    select 1
    from public.pet_responsibilities r
    where r.pet_id = p_pet_id
      and r.status = 'ACTIVE'
      and r.organization_id is not null
      and public.is_org_member(r.organization_id)
      and exists (
        select 1
        from public.organization_memberships m
        where m.organization_id = r.organization_id
          and m.user_id = p_actor
          and m.status = 'ACTIVE'
          and m.role_code in ('OWNER', 'ADMIN')
      )
      and (r.ends_at is null or r.ends_at > timezone('utc', now()))
  );
$$;

create or replace function public.m08_actor_has_capability(
  p_pet_id uuid,
  p_capability text,
  p_actor uuid default auth.uid()
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_cap text := lower(trim(coalesce(p_capability, '')));
begin
  if p_actor is null or v_cap = '' then
    return false;
  end if;

  if public.has_permission(v_cap) then
    return true;
  end if;

  if exists (
    select 1
    from public.pet_responsibilities r
    where r.pet_id = p_pet_id
      and r.status = 'ACTIVE'
      and r.person_id = p_actor
      and (r.ends_at is null or r.ends_at > timezone('utc', now()))
      and v_cap = any (public._m08_role_default_capabilities(r.role_code))
  ) then
    return true;
  end if;

  if exists (
    select 1
    from public.pet_responsibilities r
    join public.organization_memberships m
      on m.organization_id = r.organization_id
     and m.user_id = p_actor
     and m.status = 'ACTIVE'
     and m.role_code in ('OWNER', 'ADMIN')
    where r.pet_id = p_pet_id
      and r.status = 'ACTIVE'
      and r.organization_id is not null
      and (r.ends_at is null or r.ends_at > timezone('utc', now()))
      and v_cap = any (public._m08_role_default_capabilities(r.role_code))
  ) then
    return true;
  end if;

  if exists (
    select 1
    from public.pet_authorizations a
    where a.pet_id = p_pet_id
      and a.person_id = p_actor
      and a.status = 'ACTIVE'
      and (a.valid_until is null or a.valid_until > timezone('utc', now()))
      and a.valid_from <= timezone('utc', now())
      and v_cap = any (a.capabilities)
  ) then
    return true;
  end if;

  return false;
end;
$$;

create or replace function public.m08_actor_can_read_pet(
  p_pet_id uuid,
  p_actor uuid default auth.uid()
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.m08_actor_has_capability(p_pet_id, 'pet.read', p_actor)
      or public.has_permission('pet.read')
      or public.has_permission('pet.view_history');
$$;

create or replace function public.m08_require_capability(
  p_pet_id uuid,
  p_capability text
)
returns void
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public._m08_require_authenticated();
  if not public.m08_actor_has_capability(p_pet_id, p_capability) then
    raise exception 'FORBIDDEN';
  end if;
end;
$$;

-- Guard owner_id: bloquea UPDATE directo por roles cliente (authenticated/anon).
-- RPCs SECURITY DEFINER ejecutan como owner → current_user no es authenticated.
create or replace function public._m08_pets_guard_owner_id()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'UPDATE'
     and new.owner_id is distinct from old.owner_id
     and current_user in ('authenticated', 'anon') then
    raise exception 'PET_OWNER_ID_DIRECT_FORBIDDEN';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_m08_pets_guard_owner_id on public.pets;
create trigger trg_m08_pets_guard_owner_id
  before update of owner_id on public.pets
  for each row
  execute function public._m08_pets_guard_owner_id();

-- Normaliza microchip en INSERT/UPDATE de microchip_id
create or replace function public._m08_pets_normalize_microchip_trg()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  new.microchip_normalized := public.m08_normalize_microchip(new.microchip_id);
  return new;
end;
$$;

drop trigger if exists trg_m08_pets_normalize_microchip on public.pets;
create trigger trg_m08_pets_normalize_microchip
  before insert or update of microchip_id on public.pets
  for each row
  execute function public._m08_pets_normalize_microchip_trg();

-- Append-only: bloquear UPDATE/DELETE de historial para clientes
create or replace function public._m08_status_history_append_only()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if current_user in ('authenticated', 'anon') then
    raise exception 'PET_STATUS_HISTORY_IMMUTABLE';
  end if;
  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_m08_status_history_immutable on public.pet_status_history;
create trigger trg_m08_status_history_immutable
  before update or delete on public.pet_status_history
  for each row
  execute function public._m08_status_history_append_only();

-- ---------------------------------------------------------------------------
-- 8–9. Backfill + validación
-- ---------------------------------------------------------------------------
do $$
declare
  v_null_owners int;
  v_pets int;
  v_principals int;
  v_dup_chips int;
  v_system uuid;
begin
  select count(*) into v_null_owners from public.pets where owner_id is null;
  if v_null_owners > 0 then
    raise exception
      'M08_035_BACKFILL_ABORT: % pets with null owner_id before backfill (unexpected on legacy schema)',
      v_null_owners;
  end if;

  update public.pets
  set status = 'ACTIVE',
      microchip_normalized = public.m08_normalize_microchip(microchip_id)
  where true;

  select count(*) into v_pets from public.pets;

  insert into public.pet_responsibilities (
    pet_id, role_code, person_id, organization_id, status,
    starts_at, created_by, created_at, accepted_at, reason
  )
  select
    p.id,
    'PRINCIPAL',
    p.owner_id,
    null,
    'ACTIVE',
    coalesce(p.created_at, timezone('utc', now())),
    p.owner_id,
    coalesce(p.created_at, timezone('utc', now())),
    coalesce(p.created_at, timezone('utc', now())),
    'BACKFILL_035'
  from public.pets p
  where p.owner_id is not null
    and not exists (
      select 1
      from public.pet_responsibilities r
      where r.pet_id = p.id
        and r.role_code = 'PRINCIPAL'
        and r.status = 'ACTIVE'
    );

  -- history seed: use first owner as changed_by when available
  insert into public.pet_status_history (
    pet_id, previous_status, new_status, changed_by, changed_at, reason, metadata
  )
  select
    p.id,
    null,
    'ACTIVE',
    p.owner_id,
    coalesce(p.created_at, timezone('utc', now())),
    'BACKFILL_035',
    jsonb_build_object('source', 'migration_035')
  from public.pets p
  where p.owner_id is not null
    and not exists (
      select 1 from public.pet_status_history h where h.pet_id = p.id and h.reason = 'BACKFILL_035'
    );

  select count(*) into v_principals
  from public.pet_responsibilities
  where role_code = 'PRINCIPAL' and status = 'ACTIVE';

  if v_principals <> v_pets then
    raise exception
      'M08_035_BACKFILL_ABORT: principal count % != pets count %',
      v_principals, v_pets;
  end if;

  if exists (
    select 1
    from public.pet_responsibilities
    group by pet_id
    having count(*) filter (where role_code = 'PRINCIPAL' and status = 'ACTIVE') > 1
  ) then
    raise exception 'M08_035_BACKFILL_ABORT: pets with >1 active principal';
  end if;

  select count(*) into v_dup_chips
  from (
    select microchip_normalized
    from public.pets
    where status = 'ACTIVE'
      and microchip_normalized is not null
    group by microchip_normalized
    having count(*) > 1
  ) d;

  if v_dup_chips > 0 then
    raise exception
      'M08_035_BACKFILL_ABORT: % duplicate microchip_normalized among ACTIVE pets; resolve manually before unique index',
      v_dup_chips;
  end if;
end;
$$;

-- Soft-unique microchip (después del backfill fail-closed)
create unique index pets_microchip_active_uniq
  on public.pets (microchip_normalized)
  where microchip_normalized is not null and status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- 10–12. RPC públicas + funciones internas de flujo
-- ---------------------------------------------------------------------------
create or replace function public.m08_create_pet_with_principal(
  p_name text,
  p_species text,
  p_sex text,
  p_size text,
  p_description text,
  p_organization_id uuid default null,
  p_microchip_id text default null
)
returns public.pets
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_pet public.pets;
  v_owner uuid;
begin
  if coalesce(trim(p_name), '') = '' then
    raise exception 'PET_NAME_REQUIRED';
  end if;

  if p_organization_id is not null then
    if not public.is_org_member(p_organization_id) then
      raise exception 'FORBIDDEN';
    end if;
    if not exists (
      select 1 from public.organization_memberships m
      where m.organization_id = p_organization_id
        and m.user_id = v_actor
        and m.status = 'ACTIVE'
        and m.role_code in ('OWNER', 'ADMIN')
    ) then
      raise exception 'FORBIDDEN';
    end if;
    v_owner := null;
  else
    v_owner := v_actor;
  end if;

  -- pet.create: staff permission OR any authenticated creating as self/org admin
  if p_organization_id is null and not (
    public.has_permission('pet.create') or true
  ) then
    null;
  end if;

  insert into public.pets (
    owner_id, name, species, sex, size, description,
    age_years, age_months, vaccinations, reminders,
    status, microchip_id, microchip_normalized, created_at, updated_at
  ) values (
    v_owner,
    trim(p_name),
    coalesce(nullif(trim(p_species), ''), 'UNKNOWN'),
    coalesce(nullif(trim(p_sex), ''), 'UNKNOWN'),
    coalesce(nullif(trim(p_size), ''), 'UNKNOWN'),
    coalesce(p_description, ''),
    0, 0, '[]'::jsonb, '[]'::jsonb,
    'ACTIVE',
    nullif(trim(coalesce(p_microchip_id, '')), ''),
    public.m08_normalize_microchip(p_microchip_id),
    timezone('utc', now()),
    timezone('utc', now())
  )
  returning * into v_pet;

  insert into public.pet_responsibilities (
    pet_id, role_code, person_id, organization_id, status,
    starts_at, created_by, accepted_at
  ) values (
    v_pet.id,
    'PRINCIPAL',
    case when p_organization_id is null then v_actor else null end,
    p_organization_id,
    'ACTIVE',
    timezone('utc', now()),
    v_actor,
    timezone('utc', now())
  );

  perform public._m08_write_status_history(
    v_pet.id, null, 'ACTIVE', v_actor, 'CREATED', jsonb_build_object('rpc', 'm08_create_pet_with_principal')
  );

  return v_pet;
end;
$$;

create or replace function public.m08_assign_pet_responsibility(
  p_pet_id uuid,
  p_role_code text,
  p_person_id uuid default null,
  p_organization_id uuid default null,
  p_ends_at timestamptz default null,
  p_reason text default null
)
returns public.pet_responsibilities
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_row public.pet_responsibilities;
  v_pet public.pets;
  v_role text := upper(trim(coalesce(p_role_code, '')));
begin
  select * into v_pet from public.pets where id = p_pet_id for update;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status <> 'ACTIVE' then
    raise exception 'PET_NOT_ACTIVE';
  end if;

  perform public.m08_require_capability(p_pet_id, 'pet.manage_responsibilities');

  if v_role = 'PRINCIPAL' then
    raise exception 'PET_PRINCIPAL_USE_TRANSFER';
  end if;
  if v_role <> all (public.m08_pet_responsibility_roles()) then
    raise exception 'PET_ROLE_INVALID';
  end if;
  if (p_person_id is null) = (p_organization_id is null) then
    raise exception 'PET_ACTOR_XOR_REQUIRED';
  end if;
  if v_role = 'TEMPORARY_CUSTODIAN' and (p_ends_at is null or p_ends_at <= timezone('utc', now())) then
    raise exception 'PET_TEMP_CUSTODY_ENDS_REQUIRED';
  end if;

  insert into public.pet_responsibilities (
    pet_id, role_code, person_id, organization_id, status,
    starts_at, ends_at, created_by, accepted_at, reason
  ) values (
    p_pet_id, v_role, p_person_id, p_organization_id, 'ACTIVE',
    timezone('utc', now()), p_ends_at, v_actor, timezone('utc', now()), p_reason
  )
  returning * into v_row;

  return v_row;
exception
  when unique_violation then
    raise exception 'PET_RESPONSIBILITY_DUPLICATE_ACTIVE';
end;
$$;

create or replace function public.m08_revoke_pet_responsibility(p_responsibility_id uuid)
returns public.pet_responsibilities
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_row public.pet_responsibilities;
begin
  select * into v_row from public.pet_responsibilities where id = p_responsibility_id for update;
  if not found then
    raise exception 'PET_RESPONSIBILITY_NOT_FOUND';
  end if;
  if v_row.role_code = 'PRINCIPAL' and v_row.status = 'ACTIVE' then
    raise exception 'PET_PRINCIPAL_REVOKE_FORBIDDEN';
  end if;
  if v_row.status <> 'ACTIVE' then
    raise exception 'PET_RESPONSIBILITY_NOT_ACTIVE';
  end if;

  perform public.m08_require_capability(v_row.pet_id, 'pet.manage_responsibilities');

  update public.pet_responsibilities
  set status = 'REVOKED',
      revoked_at = timezone('utc', now()),
      revoked_by = v_actor,
      ends_at = coalesce(ends_at, timezone('utc', now()))
  where id = p_responsibility_id
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public.m08_grant_pet_authorization(
  p_pet_id uuid,
  p_person_id uuid,
  p_capabilities text[],
  p_valid_until timestamptz default null
)
returns public.pet_authorizations
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_pet public.pets;
  v_row public.pet_authorizations;
begin
  select * into v_pet from public.pets where id = p_pet_id for update;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status <> 'ACTIVE' then
    raise exception 'PET_NOT_ACTIVE';
  end if;
  if p_person_id is null then
    raise exception 'PET_AUTH_PERSON_REQUIRED';
  end if;
  if not public.m08_capabilities_are_grantable(p_capabilities) then
    raise exception 'PET_AUTH_CAPABILITIES_INVALID';
  end if;

  perform public.m08_require_capability(p_pet_id, 'pet.manage_authorizations');

  update public.pet_authorizations
  set status = 'SUPERSEDED',
      revoked_at = coalesce(revoked_at, timezone('utc', now())),
      revoked_by = coalesce(revoked_by, v_actor)
  where pet_id = p_pet_id
    and person_id = p_person_id
    and status = 'ACTIVE';

  insert into public.pet_authorizations (
    pet_id, person_id, granted_by, capabilities, status,
    valid_from, valid_until, accepted_at
  ) values (
    p_pet_id, p_person_id, v_actor, p_capabilities, 'ACTIVE',
    timezone('utc', now()), p_valid_until, timezone('utc', now())
  )
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public.m08_revoke_pet_authorization(p_authorization_id uuid)
returns public.pet_authorizations
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_row public.pet_authorizations;
begin
  select * into v_row from public.pet_authorizations where id = p_authorization_id for update;
  if not found then
    raise exception 'PET_AUTHORIZATION_NOT_FOUND';
  end if;
  if v_row.status <> 'ACTIVE' then
    raise exception 'PET_AUTHORIZATION_NOT_ACTIVE';
  end if;

  perform public.m08_require_capability(v_row.pet_id, 'pet.manage_authorizations');

  update public.pet_authorizations
  set status = 'REVOKED',
      revoked_at = timezone('utc', now()),
      revoked_by = v_actor
  where id = p_authorization_id
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public.m08_initiate_pet_transfer(
  p_pet_id uuid,
  p_to_person_id uuid default null,
  p_to_organization_id uuid default null,
  p_expires_at timestamptz default null
)
returns public.pet_transfers
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_pet public.pets;
  v_from_person uuid;
  v_from_org uuid;
  v_row public.pet_transfers;
  v_expires timestamptz;
begin
  select * into v_pet from public.pets where id = p_pet_id for update;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status = 'DECEASED' then
    raise exception 'PET_DECEASED_NOT_TRANSFERABLE';
  end if;
  if v_pet.status = 'ARCHIVED' then
    raise exception 'PET_ARCHIVED_NOT_TRANSFERABLE';
  end if;
  if v_pet.status <> 'ACTIVE' then
    raise exception 'PET_NOT_ACTIVE';
  end if;
  if (p_to_person_id is null) = (p_to_organization_id is null) then
    raise exception 'PET_TRANSFER_DEST_XOR_REQUIRED';
  end if;

  perform public.m08_require_capability(p_pet_id, 'pet.initiate_transfer');

  select person_id, organization_id into v_from_person, v_from_org
  from public.m08_current_principal(p_pet_id);

  if v_from_person is null and v_from_org is null then
    raise exception 'PET_PRINCIPAL_MISSING';
  end if;

  if coalesce(v_from_person::text, '') = coalesce(p_to_person_id::text, '')
     and coalesce(v_from_org::text, '') = coalesce(p_to_organization_id::text, '') then
    raise exception 'PET_TRANSFER_SAME_PRINCIPAL';
  end if;

  v_expires := coalesce(p_expires_at, timezone('utc', now()) + interval '7 days');
  if v_expires <= timezone('utc', now()) then
    raise exception 'PET_TRANSFER_EXPIRES_INVALID';
  end if;

  insert into public.pet_transfers (
    pet_id,
    from_person_id, from_organization_id,
    to_person_id, to_organization_id,
    status, requested_by, requested_at, expires_at
  ) values (
    p_pet_id,
    v_from_person, v_from_org,
    p_to_person_id, p_to_organization_id,
    'PENDING', v_actor, timezone('utc', now()), v_expires
  )
  returning * into v_row;

  return v_row;
exception
  when unique_violation then
    raise exception 'PET_TRANSFER_PENDING_EXISTS';
end;
$$;

create or replace function public.m08_accept_pet_transfer(p_transfer_id uuid)
returns public.pet_transfers
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_tr public.pet_transfers;
  v_pet public.pets;
  v_can_accept boolean := false;
begin
  select * into v_tr from public.pet_transfers where id = p_transfer_id for update;
  if not found then
    raise exception 'PET_TRANSFER_NOT_FOUND';
  end if;
  if v_tr.status <> 'PENDING' then
    raise exception 'PET_TRANSFER_NOT_PENDING';
  end if;
  if v_tr.expires_at <= timezone('utc', now()) then
    update public.pet_transfers
    set status = 'EXPIRED', responded_at = timezone('utc', now())
    where id = p_transfer_id;
    raise exception 'PET_TRANSFER_EXPIRED';
  end if;

  select * into v_pet from public.pets where id = v_tr.pet_id for update;
  if v_pet.status <> 'ACTIVE' then
    raise exception 'PET_NOT_ACTIVE';
  end if;

  if v_tr.to_person_id is not null and v_tr.to_person_id = v_actor then
    v_can_accept := true;
  elsif v_tr.to_organization_id is not null
        and public.is_org_member(v_tr.to_organization_id)
        and exists (
          select 1 from public.organization_memberships m
          where m.organization_id = v_tr.to_organization_id
            and m.user_id = v_actor
            and m.status = 'ACTIVE'
            and m.role_code in ('OWNER', 'ADMIN')
        ) then
    v_can_accept := true;
  elsif public.has_permission('pet.accept_transfer') then
    v_can_accept := true;
  end if;

  if not v_can_accept then
    raise exception 'FORBIDDEN';
  end if;

  update public.pet_responsibilities
  set status = 'SUPERSEDED',
      ends_at = coalesce(ends_at, greatest(starts_at, timezone('utc', now()))),
      revoked_at = coalesce(revoked_at, timezone('utc', now())),
      revoked_by = coalesce(revoked_by, v_actor)
  where pet_id = v_tr.pet_id
    and role_code = 'PRINCIPAL'
    and status = 'ACTIVE';

  insert into public.pet_responsibilities (
    pet_id, role_code, person_id, organization_id, status,
    starts_at, created_by, accepted_at, reason
  ) values (
    v_tr.pet_id, 'PRINCIPAL',
    v_tr.to_person_id, v_tr.to_organization_id,
    'ACTIVE', timezone('utc', now()), v_actor, timezone('utc', now()),
    'TRANSFER_ACCEPTED'
  );

  perform public._m08_sync_owner_id_from_principal(v_tr.pet_id);

  update public.pet_transfers
  set status = 'ACCEPTED',
      responded_at = timezone('utc', now()),
      responded_by = v_actor
  where id = p_transfer_id
  returning * into v_tr;

  perform public._m08_write_status_history(
    v_tr.pet_id, v_pet.status, v_pet.status, v_actor, 'TRANSFER_ACCEPTED',
    jsonb_build_object('transfer_id', p_transfer_id)
  );

  return v_tr;
end;
$$;

create or replace function public.m08_reject_pet_transfer(p_transfer_id uuid)
returns public.pet_transfers
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_tr public.pet_transfers;
  v_can boolean := false;
begin
  select * into v_tr from public.pet_transfers where id = p_transfer_id for update;
  if not found then
    raise exception 'PET_TRANSFER_NOT_FOUND';
  end if;
  if v_tr.status <> 'PENDING' then
    raise exception 'PET_TRANSFER_NOT_PENDING';
  end if;

  if v_tr.to_person_id = v_actor
     or (v_tr.to_organization_id is not null and public.is_org_member(v_tr.to_organization_id))
     or public.has_permission('pet.accept_transfer') then
    v_can := true;
  end if;
  if not v_can then
    raise exception 'FORBIDDEN';
  end if;

  update public.pet_transfers
  set status = 'REJECTED',
      responded_at = timezone('utc', now()),
      responded_by = v_actor
  where id = p_transfer_id
  returning * into v_tr;

  return v_tr;
end;
$$;

create or replace function public.m08_cancel_pet_transfer(
  p_transfer_id uuid,
  p_reason text default null
)
returns public.pet_transfers
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_tr public.pet_transfers;
begin
  select * into v_tr from public.pet_transfers where id = p_transfer_id for update;
  if not found then
    raise exception 'PET_TRANSFER_NOT_FOUND';
  end if;
  if v_tr.status <> 'PENDING' then
    raise exception 'PET_TRANSFER_NOT_PENDING';
  end if;

  if not (
    v_tr.requested_by = v_actor
    or public.m08_actor_has_capability(v_tr.pet_id, 'pet.cancel_transfer')
  ) then
    raise exception 'FORBIDDEN';
  end if;

  update public.pet_transfers
  set status = 'CANCELLED',
      cancelled_at = timezone('utc', now()),
      responded_at = timezone('utc', now()),
      responded_by = v_actor,
      cancellation_reason = p_reason
  where id = p_transfer_id
  returning * into v_tr;

  return v_tr;
end;
$$;

create or replace function public.m08_expire_pet_transfers()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer := 0;
begin
  if auth.role() is distinct from 'service_role'
     and current_setting('role', true) is distinct from 'service_role' then
    -- allow postgres/supabase_admin during migration/tests; block authenticated
    if current_user in ('authenticated', 'anon') then
      raise exception 'FORBIDDEN';
    end if;
  end if;

  update public.pet_transfers
  set status = 'EXPIRED',
      responded_at = timezone('utc', now())
  where status = 'PENDING'
    and expires_at <= timezone('utc', now());

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

create or replace function public._m08_cancel_pending_transfers(p_pet_id uuid, p_actor uuid, p_reason text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.pet_transfers
  set status = 'CANCELLED',
      cancelled_at = timezone('utc', now()),
      responded_at = timezone('utc', now()),
      responded_by = p_actor,
      cancellation_reason = p_reason
  where pet_id = p_pet_id
    and status = 'PENDING';
end;
$$;

create or replace function public.m08_mark_pet_deceased(
  p_pet_id uuid,
  p_reason text default null
)
returns public.pets
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_pet public.pets;
  v_prev text;
begin
  select * into v_pet from public.pets where id = p_pet_id for update;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status = 'DECEASED' then
    raise exception 'PET_ALREADY_DECEASED';
  end if;

  perform public.m08_require_capability(p_pet_id, 'pet.mark_deceased');
  v_prev := v_pet.status;

  perform public._m08_cancel_pending_transfers(p_pet_id, v_actor, 'PET_MARKED_DECEASED');

  update public.pet_authorizations
  set status = 'REVOKED',
      revoked_at = timezone('utc', now()),
      revoked_by = v_actor
  where pet_id = p_pet_id
    and status = 'ACTIVE';

  update public.pets
  set status = 'DECEASED',
      deceased_at = timezone('utc', now()),
      archived_at = null,
      updated_at = timezone('utc', now())
  where id = p_pet_id
  returning * into v_pet;

  perform public._m08_write_status_history(
    p_pet_id, v_prev, 'DECEASED', v_actor, coalesce(p_reason, 'MARKED_DECEASED'), '{}'::jsonb
  );

  return v_pet;
end;
$$;

create or replace function public.m08_archive_pet(
  p_pet_id uuid,
  p_reason text default null
)
returns public.pets
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_pet public.pets;
  v_prev text;
begin
  select * into v_pet from public.pets where id = p_pet_id for update;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status = 'DECEASED' then
    raise exception 'PET_DECEASED_CANNOT_ARCHIVE';
  end if;
  if v_pet.status = 'ARCHIVED' then
    raise exception 'PET_ALREADY_ARCHIVED';
  end if;

  perform public.m08_require_capability(p_pet_id, 'pet.archive');
  v_prev := v_pet.status;

  perform public._m08_cancel_pending_transfers(p_pet_id, v_actor, 'PET_ARCHIVED');

  update public.pets
  set status = 'ARCHIVED',
      archived_at = timezone('utc', now()),
      updated_at = timezone('utc', now())
  where id = p_pet_id
  returning * into v_pet;

  perform public._m08_write_status_history(
    p_pet_id, v_prev, 'ARCHIVED', v_actor, coalesce(p_reason, 'ARCHIVED'), '{}'::jsonb
  );

  return v_pet;
end;
$$;

create or replace function public.m08_restore_pet(p_pet_id uuid)
returns public.pets
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_pet public.pets;
begin
  select * into v_pet from public.pets where id = p_pet_id for update;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status = 'DECEASED' then
    raise exception 'PET_DECEASED_CANNOT_RESTORE';
  end if;
  if v_pet.status <> 'ARCHIVED' then
    raise exception 'PET_NOT_ARCHIVED';
  end if;

  perform public.m08_require_capability(p_pet_id, 'pet.restore');

  -- soft-unique microchip will raise on conflict if another ACTIVE uses same chip
  update public.pets
  set status = 'ACTIVE',
      archived_at = null,
      updated_at = timezone('utc', now())
  where id = p_pet_id
  returning * into v_pet;

  perform public._m08_write_status_history(
    p_pet_id, 'ARCHIVED', 'ACTIVE', v_actor, 'RESTORED', '{}'::jsonb
  );

  return v_pet;
exception
  when unique_violation then
    raise exception 'PET_MICROCHIP_ACTIVE_CONFLICT';
end;
$$;

create or replace function public.m08_detect_pet_duplicate_candidates(
  p_microchip text default null,
  p_name text default null
)
returns table (pet_id uuid, match_reason text)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_norm text := public.m08_normalize_microchip(p_microchip);
begin
  return query
  select p.id,
         case
           when v_norm is not null and p.microchip_normalized = v_norm then 'MICROCHIP'
           else 'NAME'
         end as match_reason
  from public.pets p
  where (
      (v_norm is not null and p.microchip_normalized = v_norm)
      or (p_name is not null and lower(p.name) = lower(trim(p_name)))
    )
    and public.m08_actor_can_read_pet(p.id, v_actor);
end;
$$;

create or replace function public.m08_set_pet_avatar_asset(
  p_pet_id uuid,
  p_asset_id uuid
)
returns public.pets
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pet public.pets;
  v_purpose text;
begin
  perform public._m08_require_authenticated();
  select * into v_pet from public.pets where id = p_pet_id for update;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;

  perform public.m08_require_capability(p_pet_id, 'pet.manage_media');

  if p_asset_id is not null then
    select purpose into v_purpose from public.file_assets where id = p_asset_id;
    if v_purpose is null then
      raise exception 'PET_AVATAR_ASSET_NOT_FOUND';
    end if;
    if v_purpose <> 'PET_AVATAR' then
      raise exception 'PET_AVATAR_PURPOSE_INVALID';
    end if;
  end if;

  update public.pets
  set avatar_file_asset_id = p_asset_id,
      updated_at = timezone('utc', now())
  where id = p_pet_id
  returning * into v_pet;

  return v_pet;
end;
$$;

-- ---------------------------------------------------------------------------
-- 13. RLS
-- ---------------------------------------------------------------------------
alter table public.pet_responsibilities enable row level security;
alter table public.pet_authorizations enable row level security;
alter table public.pet_transfers enable row level security;
alter table public.pet_status_history enable row level security;

drop policy if exists pets_select_authenticated on public.pets;
drop policy if exists pets_insert_own on public.pets;
drop policy if exists pets_update_own on public.pets;
drop policy if exists pets_delete_own on public.pets;

create policy pets_select_m08
  on public.pets for select to authenticated
  using (public.m08_actor_can_read_pet(id));

-- Escrituras de pets solo vía RPC DEFINER (table owner bypassa RLS).
-- No policies INSERT/UPDATE/DELETE for authenticated.

drop policy if exists pet_responsibilities_select_m08 on public.pet_responsibilities;
create policy pet_responsibilities_select_m08
  on public.pet_responsibilities for select to authenticated
  using (public.m08_actor_can_read_pet(pet_id));

drop policy if exists pet_authorizations_select_m08 on public.pet_authorizations;
create policy pet_authorizations_select_m08
  on public.pet_authorizations for select to authenticated
  using (
    public.m08_actor_can_read_pet(pet_id)
    or person_id = auth.uid()
  );

drop policy if exists pet_transfers_select_m08 on public.pet_transfers;
create policy pet_transfers_select_m08
  on public.pet_transfers for select to authenticated
  using (
    public.m08_actor_can_read_pet(pet_id)
    or requested_by = auth.uid()
    or to_person_id = auth.uid()
    or (to_organization_id is not null and public.is_org_member(to_organization_id))
    or (from_person_id = auth.uid())
    or (from_organization_id is not null and public.is_org_member(from_organization_id))
  );

drop policy if exists pet_status_history_select_m08 on public.pet_status_history;
create policy pet_status_history_select_m08
  on public.pet_status_history for select to authenticated
  using (
    public.m08_actor_has_capability(pet_id, 'pet.view_history')
    or public.has_permission('pet.view_history')
  );

-- ---------------------------------------------------------------------------
-- 14. Grants
-- ---------------------------------------------------------------------------
revoke insert, update, delete on public.pets from authenticated, anon;
revoke insert, update, delete on public.pet_responsibilities from authenticated, anon;
revoke insert, update, delete on public.pet_authorizations from authenticated, anon;
revoke insert, update, delete on public.pet_transfers from authenticated, anon;
revoke insert, update, delete on public.pet_status_history from authenticated, anon;

grant select on public.pets to authenticated;
grant select on public.pet_responsibilities to authenticated;
grant select on public.pet_authorizations to authenticated;
grant select on public.pet_transfers to authenticated;
grant select on public.pet_status_history to authenticated;

-- Allowlist / pure helpers
revoke all on function public.m08_pet_lifecycle_statuses() from public;
revoke all on function public.m08_pet_responsibility_roles() from public;
revoke all on function public.m08_pet_link_statuses() from public;
revoke all on function public.m08_pet_transfer_statuses() from public;
revoke all on function public.m08_pet_capability_codes() from public;
revoke all on function public.m08_pet_authorization_grantable_capabilities() from public;
revoke all on function public.m08_normalize_microchip(text) from public;
revoke all on function public.m08_capabilities_are_allowed(text[]) from public;
revoke all on function public.m08_capabilities_are_grantable(text[]) from public;

grant execute on function public.m08_pet_lifecycle_statuses() to authenticated;
grant execute on function public.m08_pet_responsibility_roles() to authenticated;
grant execute on function public.m08_pet_link_statuses() to authenticated;
grant execute on function public.m08_pet_transfer_statuses() to authenticated;
grant execute on function public.m08_pet_capability_codes() to authenticated;
grant execute on function public.m08_pet_authorization_grantable_capabilities() to authenticated;
grant execute on function public.m08_normalize_microchip(text) to authenticated;
grant execute on function public.m08_capabilities_are_allowed(text[]) to authenticated;
grant execute on function public.m08_capabilities_are_grantable(text[]) to authenticated;

-- Read helpers
revoke all on function public.m08_current_principal(uuid) from public;
revoke all on function public.m08_actor_has_active_responsibility(uuid, uuid) from public;
revoke all on function public.m08_actor_has_capability(uuid, text, uuid) from public;
revoke all on function public.m08_actor_can_read_pet(uuid, uuid) from public;
revoke all on function public.m08_require_capability(uuid, text) from public;

grant execute on function public.m08_current_principal(uuid) to authenticated;
grant execute on function public.m08_actor_has_active_responsibility(uuid, uuid) to authenticated;
grant execute on function public.m08_actor_has_capability(uuid, text, uuid) to authenticated;
grant execute on function public.m08_actor_can_read_pet(uuid, uuid) to authenticated;
-- require_capability used inside RPC; not granted to clients
grant execute on function public.m08_require_capability(uuid, text) to service_role;

-- Internals
revoke all on function public._m08_require_authenticated() from public;
revoke all on function public._m08_role_default_capabilities(text) from public;
revoke all on function public._m08_write_status_history(uuid, text, text, uuid, text, jsonb) from public;
revoke all on function public._m08_sync_owner_id_from_principal(uuid) from public;
revoke all on function public._m08_pets_guard_owner_id() from public;
revoke all on function public._m08_pets_normalize_microchip_trg() from public;
revoke all on function public._m08_status_history_append_only() from public;
revoke all on function public._m08_cancel_pending_transfers(uuid, uuid, text) from public;

grant execute on function public._m08_require_authenticated() to service_role;
grant execute on function public._m08_write_status_history(uuid, text, text, uuid, text, jsonb) to service_role;
grant execute on function public._m08_sync_owner_id_from_principal(uuid) to service_role;
grant execute on function public._m08_cancel_pending_transfers(uuid, uuid, text) to service_role;

-- Client RPCs
revoke all on function public.m08_create_pet_with_principal(text, text, text, text, text, uuid, text) from public;
revoke all on function public.m08_assign_pet_responsibility(uuid, text, uuid, uuid, timestamptz, text) from public;
revoke all on function public.m08_revoke_pet_responsibility(uuid) from public;
revoke all on function public.m08_grant_pet_authorization(uuid, uuid, text[], timestamptz) from public;
revoke all on function public.m08_revoke_pet_authorization(uuid) from public;
revoke all on function public.m08_initiate_pet_transfer(uuid, uuid, uuid, timestamptz) from public;
revoke all on function public.m08_accept_pet_transfer(uuid) from public;
revoke all on function public.m08_reject_pet_transfer(uuid) from public;
revoke all on function public.m08_cancel_pet_transfer(uuid, text) from public;
revoke all on function public.m08_expire_pet_transfers() from public;
revoke all on function public.m08_mark_pet_deceased(uuid, text) from public;
revoke all on function public.m08_archive_pet(uuid, text) from public;
revoke all on function public.m08_restore_pet(uuid) from public;
revoke all on function public.m08_detect_pet_duplicate_candidates(text, text) from public;
revoke all on function public.m08_set_pet_avatar_asset(uuid, uuid) from public;

grant execute on function public.m08_create_pet_with_principal(text, text, text, text, text, uuid, text) to authenticated;
grant execute on function public.m08_assign_pet_responsibility(uuid, text, uuid, uuid, timestamptz, text) to authenticated;
grant execute on function public.m08_revoke_pet_responsibility(uuid) to authenticated;
grant execute on function public.m08_grant_pet_authorization(uuid, uuid, text[], timestamptz) to authenticated;
grant execute on function public.m08_revoke_pet_authorization(uuid) to authenticated;
grant execute on function public.m08_initiate_pet_transfer(uuid, uuid, uuid, timestamptz) to authenticated;
grant execute on function public.m08_accept_pet_transfer(uuid) to authenticated;
grant execute on function public.m08_reject_pet_transfer(uuid) to authenticated;
grant execute on function public.m08_cancel_pet_transfer(uuid, text) to authenticated;
grant execute on function public.m08_mark_pet_deceased(uuid, text) to authenticated;
grant execute on function public.m08_archive_pet(uuid, text) to authenticated;
grant execute on function public.m08_restore_pet(uuid) to authenticated;
grant execute on function public.m08_detect_pet_duplicate_candidates(text, text) to authenticated;
grant execute on function public.m08_set_pet_avatar_asset(uuid, uuid) to authenticated;

grant execute on function public.m08_expire_pet_transfers() to service_role;

-- Explicitly deny anon on writers
revoke execute on function public.m08_create_pet_with_principal(text, text, text, text, text, uuid, text) from anon;
revoke execute on function public.m08_expire_pet_transfers() from authenticated, anon;

-- ---------------------------------------------------------------------------
-- 15. Permisos M02
-- ---------------------------------------------------------------------------
insert into public.permissions (code, description) values
  ('pet.read', 'Leer mascotas autorizadas / staff'),
  ('pet.create', 'Crear mascotas (staff amplio)'),
  ('pet.update', 'Actualizar mascotas (staff)'),
  ('pet.manage_responsibilities', 'Administrar responsabilidades de mascotas'),
  ('pet.manage_authorizations', 'Administrar autorizaciones de mascotas'),
  ('pet.initiate_transfer', 'Iniciar transferencia de mascota'),
  ('pet.accept_transfer', 'Aceptar transferencia de mascota (staff)'),
  ('pet.cancel_transfer', 'Cancelar transferencia de mascota'),
  ('pet.mark_deceased', 'Marcar mascota fallecida'),
  ('pet.archive', 'Archivar mascota'),
  ('pet.restore', 'Restaurar mascota archivada'),
  ('pet.manage_media', 'Administrar media de mascota'),
  ('pet.view_history', 'Ver historial de estado de mascota'),
  ('pet.manage_health', 'Administrar datos de salud de mascota')
on conflict (code) do nothing;

insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code = 'ADMIN'
  and p.code in ('pet.read', 'pet.view_history')
on conflict do nothing;

insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code = 'SUPERADMIN'
  and p.code like 'pet.%'
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 16. Eventos M07 (catálogo; sin envío M06 real)
-- ---------------------------------------------------------------------------
insert into public.observability_event_catalog (
  event_key, module, category, default_severity, sensitivity, organization_scoped,
  retention_policy_key, remote_persistence_allowed, analytics_allowed,
  allowed_metadata_keys, required_metadata_keys
) values
('m08.pet.created','M08','AUDIT','INFO','INTERNAL',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.updated','M08','AUDIT','INFO','INTERNAL',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.responsibility.assigned','M08','AUDIT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.responsibility.revoked','M08','AUDIT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.authorization.granted','M08','AUDIT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.authorization.revoked','M08','AUDIT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.transfer.initiated','M08','AUDIT','NOTICE','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.transfer.accepted','M08','AUDIT','NOTICE','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.transfer.rejected','M08','AUDIT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.transfer.cancelled','M08','AUDIT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.transfer.expired','M08','AUDIT','INFO','INTERNAL',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.marked_deceased','M08','AUDIT','WARNING','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.archived','M08','AUDIT','INFO','INTERNAL',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.restored','M08','AUDIT','INFO','INTERNAL',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.duplicate_detected','M08','AUDIT','NOTICE','INTERNAL',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('m08.pet.avatar_changed','M08','AUDIT','INFO','INTERNAL',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[])
on conflict (event_key) do nothing;

-- ---------------------------------------------------------------------------
-- 17. Hooks M06 (conceptual — sin envío real)
-- ---------------------------------------------------------------------------
comment on table public.pet_transfers is
  'M08 transfers. M06 hooks conceptuales categoría PET en Etapa posterior; 035 no encola notificaciones reales.';

-- ---------------------------------------------------------------------------
-- 18. Comments
-- ---------------------------------------------------------------------------
comment on table public.pet_responsibilities is
  'M08 responsibilities graph; exactly one ACTIVE PRINCIPAL per pet.';
comment on table public.pet_authorizations is
  'M08 delegated capabilities (text[]); no ownership/transfer/decease/archive/manage_responsibilities grants.';
comment on table public.pet_status_history is
  'M08 append-only lifecycle history; client UPDATE/DELETE blocked.';
comment on function public._m08_sync_owner_id_from_principal(uuid) is
  'Internal owner_id projection sync. STAGING BLOCKED until Etapa 4 adapts Android repository.';

-- ---------------------------------------------------------------------------
-- 19. Verificaciones finales
-- ---------------------------------------------------------------------------
do $$
declare
  v_missing_rpc text;
begin
  if not exists (
    select 1 from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'm08_create_pet_with_principal'
  ) then
    raise exception 'M08_035_FINAL: missing m08_create_pet_with_principal';
  end if;

  if not exists (
    select 1 from pg_indexes
    where schemaname = 'public' and indexname = 'pets_microchip_active_uniq'
  ) then
    raise exception 'M08_035_FINAL: missing pets_microchip_active_uniq';
  end if;

  if exists (
    select 1 from public.pets p
    where not exists (
      select 1 from public.pet_responsibilities r
      where r.pet_id = p.id and r.role_code = 'PRINCIPAL' and r.status = 'ACTIVE'
    )
  ) then
    raise exception 'M08_035_FINAL: pet without active principal';
  end if;

  if not exists (select 1 from public.permissions where code = 'pet.read') then
    raise exception 'M08_035_FINAL: pet.read permission missing';
  end if;

  if not exists (
    select 1 from public.observability_event_catalog where event_key = 'm08.pet.created'
  ) then
    raise exception 'M08_035_FINAL: m08.pet.created catalog missing';
  end if;
end;
$$;

commit;
