-- =============================================================================
-- LeoVer — migración 082: public_code Lost/Found (y adopciones) + pgcrypto
-- Forward-only sobre 001–081. Idempotente. No borra filas ni resetea tablas.
--
-- Causa confirmada (081):
--   public._web_generate_public_code() usa gen_random_bytes(16) sin schema
--   con search_path = public. En Supabase pgcrypto vive en `extensions`.
--   Trigger lost_found_posts_ensure_public_code (INSERT lost_found_posts)
--   y adoptions_ensure_public_code fallan con:
--     function gen_random_bytes(integer) does not exist
--
-- NO generar public_code desde Android. NO editar 081.
-- =============================================================================

begin;

create extension if not exists pgcrypto with schema extensions;

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
    v_code := 'PUB-' || encode(extensions.gen_random_bytes(16), 'hex');
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

comment on function public._web_generate_public_code() is
  '082: opaque PUB-* codes via extensions.gen_random_bytes (pgcrypto). Used by lost_found_posts and adoptions INSERT triggers.';

commit;
