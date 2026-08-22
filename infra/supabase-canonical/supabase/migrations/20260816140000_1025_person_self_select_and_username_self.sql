-- 1025_person_self_select_and_username_self
-- AUTH-02 / ONBOARDING-01
--
-- Geography catalog already has DIRECT_CATALOG_SELECT (1020 GRANT + location_nodes_read).
-- This migration does not redesign geography.
--
-- 1020 revoked ALL on public.persons then never re-granted SELECT.
-- persons_self_select RLS therefore could not be used by PostgREST.
-- Android getUser() failed, JWT fallback had no username, onboarding
-- asked for the signup username again, and canon_is_username_available
-- rejected it because PERSON already owned it.
--
-- Catalog privacy: location_nodes remain publicly readable (active rows).
-- persons.home_locality_id stays behind persons_self_select.

grant select on table public.persons to authenticated;

-- Same normalized username owned by the current PERSON is available (unchanged).
-- Unique index persons_username_uidx is unchanged. Anon signup (auth.uid() null)
-- still sees every existing username as taken.
create or replace function public.canon_is_username_available(p_username text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select not exists (
    select 1 from public.persons
    where lower(username) = lower(btrim(coalesce(p_username, '')))
      and user_id is distinct from auth.uid()
  );
$$;

grant execute on function public.canon_is_username_available(text) to anon, authenticated;
