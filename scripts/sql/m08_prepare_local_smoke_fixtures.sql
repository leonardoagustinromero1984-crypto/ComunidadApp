-- =============================================================================
-- LeoVer M08 Etapa 4C — fixtures LOCALES para smoke APK
-- LOCAL TEST DATA ONLY
-- Sin PII real, sin credenciales, sin tokens, sin privileged API keys.
-- NO USAR EN STAGING / PRODUCCIÓN.
-- Ejecutar solo contra Supabase local tras db reset (001–036).
-- =============================================================================

-- Este script NO inserta auth.users (requiere flujo Auth / smoke manual).
-- Prepara datos de referencia documentados y valida que el esquema M08 esté listo.
-- Los actores A–D se crean en smoke manual (registro en APK) o vía Auth local.

drop table if exists m08_smoke_fixture_notes;

create temporary table m08_smoke_fixture_notes (
  note text not null,
  created_at timestamptz not null default timezone('utc', now())
);

insert into m08_smoke_fixture_notes (note) values
  ('LOCAL TEST DATA ONLY — LeoVer M08 Stage 4C'),
  ('Actors (manual Auth): A principal, B co-responsible, C authorized READ, D stranger'),
  ('Scenarios: person principal, co-resp, auth READ, org principal owner_id null, ARCHIVED, DECEASED, microchip conflict, PET_AVATAR M05'),
  ('Do not commit passwords, tokens, anon keys, or privileged API keys'),
  ('Public profile: foreign pets must remain hidden');

-- Sanity: RPC M08 presentes (035+036)
do $$
declare
  v_missing text[] := array[]::text[];
  r text;
begin
  foreach r in array array[
    'm08_create_pet_with_principal',
    'm08_update_pet_profile',
    'm08_update_pet_health',
    'm08_list_accessible_pets',
    'm08_get_pet_access_context',
    'm08_archive_pet',
    'm08_set_pet_avatar_asset'
  ]
  loop
    if not exists (
      select 1 from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = r
    ) then
      v_missing := array_append(v_missing, r);
    end if;
  end loop;

  if cardinality(v_missing) > 0 then
    raise exception 'M08_SMOKE_FIXTURES: missing RPCs: %', array_to_string(v_missing, ', ');
  end if;

  if exists (
    select 1 from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'm08_list_public_profile_pets'
  ) then
    raise exception 'M08_SMOKE_FIXTURES: public profile pets RPC must stay absent';
  end if;
end;
$$;

select note from m08_smoke_fixture_notes order by created_at;

select
  'm08_smoke_fixtures_ready' as status,
  'PASS' as result,
  'Schema RPCs OK; create actors via local Auth during smoke' as detail;
