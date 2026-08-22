-- =============================================================================
-- LeoVer / ComunidadApp
-- DEVELOPMENT ONLY — seed QA post-reset
-- DO NOT RUN IN PRODUCTION
-- NO autoejecutar en CI/deploy.
--
-- No hardcodea UUIDs. Primero crear las cuentas por signup en la app
-- (identidad PERSON). Después sustituir los placeholders y ejecutar.
--
-- Cuentas esperadas (signup real, no inventar):
--   :persona_id   — Persona QA
--   :shelter_owner_id — dueño del refugio QA
--   :foster_id    — Persona con hogar de tránsito
--   :provider_id  — Persona prestadora / vet
-- =============================================================================

-- select set_config('leover.qa_seed.persona_id', '<uuid>', false);
-- select set_config('leover.qa_seed.shelter_owner_id', '<uuid>', false);
-- select set_config('leover.qa_seed.foster_id', '<uuid>', false);
-- select set_config('leover.qa_seed.provider_id', '<uuid>', false);

do $$
declare
  persona uuid := nullif(current_setting('leover.qa_seed.persona_id', true), '')::uuid;
  shelter_owner uuid := nullif(current_setting('leover.qa_seed.shelter_owner_id', true), '')::uuid;
  foster uuid := nullif(current_setting('leover.qa_seed.foster_id', true), '')::uuid;
  provider uuid := nullif(current_setting('leover.qa_seed.provider_id', true), '')::uuid;
  org_id uuid;
begin
  if persona is null or shelter_owner is null or foster is null or provider is null then
    raise exception using
      message = 'QA SEED BLOCKED',
      detail = 'Definí leover.qa_seed.persona_id / shelter_owner_id / foster_id / provider_id '
               || 'con UUIDs de auth.users creados por signup. No inventar IDs.',
      hint = 'NO se insertó nada.';
  end if;

  if (select count(*) from public.users
      where id in (persona, shelter_owner, foster, provider)) <> 4 then
    raise exception 'Los 4 UUIDs deben existir en public.users (signup + trigger).';
  end if;

  update public.users
  set account_type = 'PERSON'
  where id in (persona, shelter_owner, foster, provider)
    and coalesce(account_type, 'PERSON') <> 'PERSON';

  insert into public.organizations (
    slug, legal_name, display_name, type, description, status,
    country_code, province, city, created_by
  ) values (
    'refugio-qa-leover',
    'Refugio QA LeoVer',
    'Refugio QA LeoVer',
    'SHELTER',
    'Organización de prueba para validar contexto organizacional.',
    'ACTIVE',
    'AR',
    'Buenos Aires',
    'San Vicente',
    shelter_owner
  )
  on conflict (slug) do update
    set display_name = excluded.display_name,
        status = 'ACTIVE'
  returning id into org_id;

  if org_id is null then
    select id into org_id from public.organizations where slug = 'refugio-qa-leover';
  end if;

  if not exists (
    select 1 from public.organization_memberships
    where organization_id = org_id and user_id = shelter_owner and status = 'ACTIVE'
  ) then
    insert into public.organization_memberships (
      organization_id, user_id, role_code, status, joined_at
    ) values (
      org_id, shelter_owner, 'OWNER', 'ACTIVE', timezone('utc', now())
    );
  end if;

  if not exists (
    select 1 from public.foster_home_profiles
    where owner_user_id = foster and status <> 'CLOSED'
  ) then
    insert into public.foster_home_profiles (
      owner_user_id, display_name, description, status, availability_status,
      total_capacity, zone_text, public_location_text
    ) values (
      foster,
      'Hogar de tránsito QA',
      'Perfil personal de tránsito para validar contexto FOSTER.',
      'ACTIVE',
      'AVAILABLE',
      2,
      'San Vicente',
      'San Vicente, Buenos Aires'
    );
  end if;

  if not exists (
    select 1 from public.m22_service_providers
    where owner_user_id = provider and status <> 'ARCHIVED'
  ) then
    insert into public.m22_service_providers (
      owner_user_id, display_name, category, description, city, status
    ) values (
      provider,
      'Consultorio QA LeoVer',
      'VET',
      'Prestador de prueba para validar contexto PROVIDER/VETERINARY.',
      'San Vicente',
      'ACTIVE'
    );
  end if;

  raise notice 'QA seed inserted. org_id=% persona=% shelter_owner=% foster=% provider=%',
    org_id, persona, shelter_owner, foster, provider;
end $$;
