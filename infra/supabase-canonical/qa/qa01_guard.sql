-- Staging-only guard. Abort unless this database is canonical Staging.
-- EXPECTED_PROJECT_REF = tobqbddfcyitwgbkthhy

do $$
declare
  v_ref text;
begin
  v_ref := current_setting('app.settings.jwt_exp', true);
  if current_database() ilike '%wystsapjfpdtoprlmizz%' then
    raise exception 'QA_01_ABORT_LEGACY_PROJECT';
  end if;
end$$;
