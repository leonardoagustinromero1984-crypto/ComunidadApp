-- =============================================================================
-- LeoVer Web — migración 081: páginas públicas compartibles (anon RPCs)
-- Forward-only sobre 001–080. Contratos sanitizados para web/leover.com.ar
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Identificadores públicos opacos (reutiliza patrón M14 PUB-*)
-- ---------------------------------------------------------------------------
create or replace function public._web_generate_public_code()
returns text
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
  v_code text;
begin
  loop
    v_code := 'PUB-' || encode(gen_random_bytes(16), 'hex');
    exit when not exists (
      select 1
      from (
        select public_code as code from public.adoptions
        union all
        select public_code from public.lost_found_posts
        union all
        select public_code from public.pet_passports
      ) codes
      where code = v_code
    );
  end loop;
  return v_code;
end;
$$;

alter table public.adoptions
  add column if not exists public_code text;

alter table public.lost_found_posts
  add column if not exists public_code text;

update public.adoptions
set public_code = public._web_generate_public_code()
where public_code is null or trim(public_code) = '';

update public.lost_found_posts
set public_code = public._web_generate_public_code()
where public_code is null or trim(public_code) = '';

alter table public.adoptions
  alter column public_code set not null;

alter table public.lost_found_posts
  alter column public_code set not null;

create unique index if not exists adoptions_public_code_uidx
  on public.adoptions (public_code);

create unique index if not exists lost_found_posts_public_code_uidx
  on public.lost_found_posts (public_code);

create or replace function public._web_ensure_public_code()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.public_code is null or trim(new.public_code) = '' then
    new.public_code := public._web_generate_public_code();
  end if;
  return new;
end;
$$;

drop trigger if exists adoptions_ensure_public_code on public.adoptions;
create trigger adoptions_ensure_public_code
  before insert on public.adoptions
  for each row
  execute function public._web_ensure_public_code();

drop trigger if exists lost_found_posts_ensure_public_code on public.lost_found_posts;
create trigger lost_found_posts_ensure_public_code
  before insert on public.lost_found_posts
  for each row
  execute function public._web_ensure_public_code();

-- ---------------------------------------------------------------------------
-- 2. Helpers — moderación, imagen pública, zona aproximada
-- ---------------------------------------------------------------------------
create or replace function public._web_is_content_blocked(
  p_target_type text,
  p_target_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.moderation_actions a
    where a.target_type = p_target_type
      and a.target_id = p_target_id::text
      and a.action_type in ('CONTENT_HIDDEN', 'CONTENT_REMOVED')
      and a.reversed_at is null
  );
$$;

create or replace function public._web_sanitize_public_image(
  p_legacy_url text,
  p_asset_id uuid default null
)
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_resolved jsonb;
  v_bucket text;
  v_path text;
  v_url text := nullif(trim(coalesce(p_legacy_url, '')), '');
begin
  if v_url is not null
     and v_url ~ '^https://'
     and v_url ~* '/storage/v1/object/public/' then
    return v_url;
  end if;

  if p_asset_id is not null then
    begin
      v_resolved := public.resolve_public_file_asset(p_asset_id);
      v_bucket := v_resolved->>'bucket';
      v_path := v_resolved->>'path';
      if v_bucket is not null and v_path is not null then
        return format('storage:%s/%s', v_bucket, v_path);
      end if;
    exception
      when others then
        return null;
    end;
  end if;

  return null;
end;
$$;

create or replace function public._web_public_zone_text(p_location text)
returns text
language plpgsql
immutable
as $$
declare
  v text := nullif(trim(coalesce(p_location, '')), '');
begin
  if v is null then
    return null;
  end if;
  if v ~* '^\s*-?\d+(\.\d+)?\s*,\s*-?\d+(\.\d+)?\s*$' then
    return null;
  end if;
  return v;
end;
$$;

create or replace function public._web_publisher_display_name(
  p_publisher_name text,
  p_organization_id uuid
)
returns text
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    nullif(trim(p_publisher_name), ''),
    (
      select coalesce(o.display_name, o.legal_name)
      from public.organizations o
      where o.id = p_organization_id
    ),
    'Rescatista LeoVer'
  );
$$;

-- ---------------------------------------------------------------------------
-- 3. JSON sanitizado — adopción (M09)
-- ---------------------------------------------------------------------------
create or replace function public._web_public_adoption_json(p_row public.adoptions)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_photo text;
  v_pet public.pets;
begin
  if public._web_is_content_blocked('ADOPTION_LISTING', p_row.id) then
    return null;
  end if;

  if p_row.status not in ('PUBLISHED', 'ADOPTED', 'CLOSED') then
    return null;
  end if;

  v_photo := public._web_sanitize_public_image(p_row.photo_url, null);

  if p_row.pet_id is not null then
    select * into v_pet from public.pets where id = p_row.pet_id;
    if found then
      v_photo := coalesce(
        v_photo,
        public._web_sanitize_public_image(v_pet.photo_url, v_pet.avatar_file_asset_id)
      );
    end if;
  end if;

  return jsonb_strip_nulls(jsonb_build_object(
    'public_code', p_row.public_code,
    'title', nullif(trim(coalesce(p_row.title, p_row.name, '')), ''),
    'name', nullif(trim(coalesce(p_row.name, '')), ''),
    'description', nullif(trim(coalesce(p_row.description, '')), ''),
    'requirements', nullif(trim(coalesce(p_row.requirements, '')), ''),
    'species', nullif(trim(coalesce(p_row.species, '')), ''),
    'sex', nullif(trim(coalesce(p_row.sex, '')), ''),
    'age_years', p_row.age_years,
    'age_months', p_row.age_months,
    'size', nullif(trim(coalesce(p_row.size, '')), ''),
    'status', p_row.status,
    'is_active', p_row.status = 'PUBLISHED',
    'location_text', public._web_public_zone_text(coalesce(p_row.location_text, p_row.location)),
    'photo_url', v_photo,
    'publisher_display_name', public._web_publisher_display_name(
      p_row.publisher_name,
      p_row.publisher_organization_id
    ),
    'published_at', p_row.published_at,
    'updated_at', p_row.updated_at
  ));
end;
$$;

create or replace function public.get_public_adoption(p_public_code text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_row public.adoptions;
  v_json jsonb;
begin
  select * into v_row
  from public.adoptions
  where public_code = trim(coalesce(p_public_code, ''));

  if not found then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
  end if;

  v_json := public._web_public_adoption_json(v_row);
  if v_json is null then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
  end if;

  return v_json;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. JSON sanitizado — perdido / encontrado (lost_found_posts + M13 zone pattern)
-- ---------------------------------------------------------------------------
create or replace function public._web_public_lost_found_json(
  p_row public.lost_found_posts,
  p_expected_type text
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_photo text;
begin
  if upper(trim(coalesce(p_row.type, ''))) <> upper(trim(p_expected_type)) then
    return null;
  end if;

  if public._web_is_content_blocked('LOST_FOUND_CASE', p_row.id) then
    return null;
  end if;

  if p_row.status not in ('ACTIVE', 'RESOLVED') then
    return null;
  end if;

  v_photo := public._web_sanitize_public_image(p_row.photo_url, null);

  return jsonb_strip_nulls(jsonb_build_object(
    'public_code', p_row.public_code,
    'case_type', upper(trim(p_row.type)),
    'pet_name', nullif(trim(coalesce(p_row.pet_name, '')), ''),
    'species', nullif(trim(coalesce(p_row.species, '')), ''),
    'description', nullif(trim(coalesce(p_row.description, '')), ''),
    'zone_text', public._web_public_zone_text(p_row.location),
    'status', p_row.status,
    'is_active', p_row.status = 'ACTIVE',
    'photo_url', v_photo,
    'created_at', p_row.created_at,
    'updated_at', p_row.updated_at
  ));
end;
$$;

create or replace function public.get_public_lost_case(p_public_code text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_row public.lost_found_posts;
  v_json jsonb;
begin
  select * into v_row
  from public.lost_found_posts
  where public_code = trim(coalesce(p_public_code, ''));

  if not found then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
  end if;

  v_json := public._web_public_lost_found_json(v_row, 'LOST');
  if v_json is null then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
  end if;

  return v_json;
end;
$$;

create or replace function public.get_public_found_case(p_public_code text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_row public.lost_found_posts;
  v_json jsonb;
begin
  select * into v_row
  from public.lost_found_posts
  where public_code = trim(coalesce(p_public_code, ''));

  if not found then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
  end if;

  v_json := public._web_public_lost_found_json(v_row, 'FOUND');
  if v_json is null then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
  end if;

  return v_json;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. Mascota pública — delega en M14 (Pasaporte PUBLIC_REDACTED)
-- ---------------------------------------------------------------------------
create or replace function public.get_public_pet(p_public_code text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_passport public.pet_passports;
  v_passport_json jsonb;
  v_code text := trim(coalesce(p_public_code, ''));
begin
  if v_code = '' then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
  end if;

  select * into v_passport
  from public.pet_passports
  where public_code = v_code
    and status = 'ACTIVE'
    and visibility = 'PUBLIC_REDACTED';

  if not found then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
  end if;

  if public._web_is_content_blocked('PET_PROFILE', v_passport.pet_id) then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
  end if;

  v_passport_json := public.m14_get_public_pet_passport(v_code);

  return v_passport_json
    || jsonb_build_object(
      'public_code', v_code,
      'page_kind', 'pet_passport',
      'photo_url', null
    );
exception
  when others then
    raise exception using errcode = 'P0001', message = 'NOT_PUBLIC';
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Privileges
-- ---------------------------------------------------------------------------
revoke all on function public._web_generate_public_code() from public;
revoke all on function public._web_ensure_public_code() from public;
revoke all on function public._web_is_content_blocked(text, uuid) from public;
revoke all on function public._web_sanitize_public_image(text, uuid) from public;
revoke all on function public._web_public_zone_text(text) from public;
revoke all on function public._web_publisher_display_name(text, uuid) from public;
revoke all on function public._web_public_adoption_json(public.adoptions) from public;
revoke all on function public._web_public_lost_found_json(public.lost_found_posts, text) from public;

revoke all on function public.get_public_adoption(text) from public;
revoke all on function public.get_public_lost_case(text) from public;
revoke all on function public.get_public_found_case(text) from public;
revoke all on function public.get_public_pet(text) from public;

grant execute on function public.get_public_adoption(text) to anon, authenticated;
grant execute on function public.get_public_lost_case(text) to anon, authenticated;
grant execute on function public.get_public_found_case(text) to anon, authenticated;
grant execute on function public.get_public_pet(text) to anon, authenticated;

comment on function public.get_public_adoption(text) is
  'Web pública: publicación de adopción sanitizada (M09). Sin PII ni IDs internos.';

comment on function public.get_public_lost_case(text) is
  'Web pública: caso perdido sanitizado. Sin contacto, coords ni autor.';

comment on function public.get_public_found_case(text) is
  'Web pública: caso encontrado sanitizado. Sin contacto, coords ni autor.';

comment on function public.get_public_pet(text) is
  'Web pública: identidad de mascota vía Pasaporte M14 PUBLIC_REDACTED.';

commit;
