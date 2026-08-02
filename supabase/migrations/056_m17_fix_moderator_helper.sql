-- =============================================================================
-- LeoVer M17 — migración 056: corrección _m17_is_moderator
-- Forward-only. No modifica 054/055 aplicadas.
-- Reemplaza referencia errónea a platform_role_assignments por user_has_active_role (M02).
-- =============================================================================

begin;

create or replace function public._m17_is_moderator(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_user is not null and (
    public.user_has_active_role(p_user, 'MODERATOR')
    or public.user_has_active_role(p_user, 'ADMIN')
    or public.user_has_active_role(p_user, 'SUPERADMIN')
  );
$$;

revoke all on function public._m17_is_moderator(uuid) from public;
grant execute on function public._m17_is_moderator(uuid) to authenticated;

commit;
