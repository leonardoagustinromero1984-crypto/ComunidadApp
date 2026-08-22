-- LeoVer Canonical
-- Logical migration: 1040
-- Allow the signed-in PERSON to update only privacy_state.
--
-- Why: 1025 granted SELECT on public.persons, never UPDATE. A full UPDATE grant
-- would expose username/birth_date to PostgREST. Column grant + existing
-- persons_self_update RLS lets Settings > Privacidad persist visibility
-- without a new RPC and without widening other PERSON columns.

grant update (privacy_state) on table public.persons to authenticated;
