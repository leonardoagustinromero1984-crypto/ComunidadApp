-- LeoVer Canonical
-- Logical migration: 1029
-- UX-03 / ORG-02: atomic org create is retry-safe; foster profile readable after upsert.
-- Forward-only. Do not edit 1000-1028.

drop function if exists public.canon_create_organization(text, text, text);

create or replace function public.canon_create_organization(
  p_name text,
  p_slug text,
  p_capability text,
  p_home_locality_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_role uuid;
  v_capability text;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_age_allows(auth.uid(), 'org.create') then
    raise exception 'AGE_CAPABILITY_DENIED';
  end if;
  v_capability := upper(btrim(p_capability));
  if v_capability not in ('SHELTER', 'NGO', 'VETERINARY_CLINIC', 'DAYCARE', 'PROVIDER', 'OTHER') then
    v_capability := 'OTHER';
  end if;

  select o.id into v_id
  from public.organizations o
  where lower(o.slug) = lower(btrim(p_slug))
  limit 1;

  if v_id is not null then
    if exists (
      select 1
      from public.organization_memberships m
      where m.organization_id = v_id
        and m.person_id = auth.uid()
        and m.status = 'ACTIVE'
    ) then
      return v_id;
    end if;
    raise exception 'ORGANIZATION_SLUG_TAKEN';
  end if;

  insert into public.organizations (name, slug, created_by_user_id, primary_label, home_locality_id)
  values (p_name, p_slug, auth.uid(), v_capability, p_home_locality_id)
  returning id into v_id;

  insert into public.organization_capabilities (organization_id, capability)
  values (v_id, v_capability)
  on conflict do nothing;

  insert into public.organization_roles (organization_id, code, name, is_system)
  values (v_id, 'OWNER', 'Owner', true)
  returning id into v_role;

  insert into public.organization_role_permissions (role_id, permission_code)
  select v_role, code from public.permission_codes where scope = 'ORG';

  insert into public.organization_memberships (organization_id, person_id, role_id, status)
  values (v_id, auth.uid(), v_role, 'ACTIVE');

  insert into public.organization_public_profiles (organization_id)
  values (v_id)
  on conflict do nothing;

  perform public.canon_audit('org.create', 'organizations', v_id, '{}'::jsonb);
  return v_id;
end;
$$;

create or replace function public.canon_get_my_foster_profile()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_build_object(
      'user_id', f.user_id,
      'capacity', f.capacity,
      'active', f.active,
      'locality_id', f.locality_id
    )
    from public.foster_profiles f
    where f.user_id = auth.uid()
  ), 'null'::jsonb);
end;
$$;

do $$
declare r record;
begin
  for r in
    select p.proname, pg_get_function_identity_arguments(p.oid) as args
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'canon_create_organization',
        'canon_get_my_foster_profile'
      )
  loop
    execute format('revoke all on function public.%I(%s) from public, anon', r.proname, r.args);
    execute format('grant execute on function public.%I(%s) to authenticated', r.proname, r.args);
  end loop;
end$$;
