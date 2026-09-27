-- 1057: administrative staff (technical identities) + catalog permissions.
-- Does not edit 1055/1056. No credentials. No physical deletes.

-- ---------------------------------------------------------------------------
-- Identity: internal display name
-- ---------------------------------------------------------------------------
alter table public.platform_admin_identities
  add column if not exists display_name text not null default '';

update public.platform_admin_identities
  set display_name = username_normalized
  where btrim(display_name) = '';

-- ---------------------------------------------------------------------------
-- SUPPORT role + staff/catalog permissions
-- ---------------------------------------------------------------------------
insert into public.platform_roles (code, description) values
  ('SUPPORT', 'Platform support')
on conflict (code) do nothing;

insert into public.platform_permissions (code, description) values
  ('catalogs.view', 'View master catalogs'),
  ('catalogs.manage', 'Manage master catalogs'),
  ('staff.view', 'View administrative staff'),
  ('staff.manage', 'Manage administrative staff')
on conflict (code) do nothing;

insert into public.platform_role_permissions (role_code, permission_code)
select r.role_code, p.code
from (
  values
    ('SUPPORT', 'profile.read.own'),
    ('SUPPORT', 'profile.update.own'),
    ('SUPPORT', 'profile.read.public'),
    ('SUPPORT', 'support.view'),
    ('SUPPORT', 'support.manage'),
    ('SUPPORT', 'support.view_sensitive'),
    ('SUPPORT', 'users.view_private'),
    ('ADMIN', 'catalogs.view'),
    ('ADMIN', 'catalogs.manage'),
    ('SUPERADMIN', 'catalogs.view'),
    ('SUPERADMIN', 'catalogs.manage'),
    ('SUPERADMIN', 'staff.view'),
    ('SUPERADMIN', 'staff.manage')
) as r(role_code, code)
join public.platform_permissions p on p.code = r.code
on conflict do nothing;

insert into public.platform_role_permissions (role_code, permission_code)
select 'SUPERADMIN', p.code from public.platform_permissions p
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Login: SUPPORT technical identities
-- ---------------------------------------------------------------------------
create or replace function public.admin_begin_login(p_username text, p_password text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_user uuid;
  v_email text;
  v_hash text;
  v_dummy text := crypt('leover-admin-timing', gen_salt('bf', 4));
  v_ok boolean := false;
begin
  if char_length(coalesce(p_password, '')) < 1 then
    raise exception 'INVALID_CREDENTIALS';
  end if;
  select i.user_id, u.email, u.encrypted_password
    into v_user, v_email, v_hash
  from public.platform_admin_identities i
  join auth.users u on u.id = i.user_id
  where i.username_normalized = lower(btrim(coalesce(p_username, '')))
    and i.disabled_at is null
  limit 1;
  v_ok := (
    v_user is not null
    and v_hash is not null
    and crypt(p_password, v_hash) = v_hash
  );
  if not v_ok then
    perform crypt(coalesce(p_password, 'x'), coalesce(v_hash, v_dummy));
    raise exception 'INVALID_CREDENTIALS';
  end if;
  if not public._acl_platform_role(v_user, 'SUPERADMIN')
     and not public._acl_platform_role(v_user, 'ADMIN')
     and not public._acl_platform_role(v_user, 'MODERATOR')
     and not public._acl_platform_role(v_user, 'SUPPORT') then
    raise exception 'INVALID_CREDENTIALS';
  end if;
  return jsonb_build_object('email', v_email);
end;
$$;

grant execute on function public.admin_begin_login(text, text) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Staff RPCs
-- ---------------------------------------------------------------------------
create or replace function public.list_admin_staff(p_query text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  q text := lower(btrim(coalesce(p_query, '')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.view') then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(row_to_json(x) order by x.display_name, x.username)
    from (
      select
        i.user_id,
        i.display_name,
        i.username_normalized as username,
        coalesce((
          select a.role_code
          from public.user_platform_role_assignments a
          where a.user_id = i.user_id
            and a.revoked_at is null
            and a.role_code in ('SUPERADMIN', 'ADMIN', 'MODERATOR', 'SUPPORT')
          order by case a.role_code
            when 'SUPERADMIN' then 1
            when 'ADMIN' then 2
            when 'MODERATOR' then 3
            else 4
          end
          limit 1
        ), 'USER') as role_code,
        (i.disabled_at is null) as active,
        i.must_change_password,
        i.is_root,
        i.created_at,
        u.last_sign_in_at
      from public.platform_admin_identities i
      join auth.users u on u.id = i.user_id
      where q = ''
         or i.username_normalized like '%' || q || '%'
         or lower(i.display_name) like '%' || q || '%'
    ) x
  ), '[]'::jsonb);
end;
$$;

create or replace function public.get_admin_staff(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  i public.platform_admin_identities%rowtype;
  v_role text;
  v_last timestamptz;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.view') then raise exception 'FORBIDDEN'; end if;
  if p_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  select * into i from public.platform_admin_identities where user_id = p_user_id;
  if not found then raise exception 'STAFF_NOT_FOUND'; end if;
  select a.role_code into v_role
  from public.user_platform_role_assignments a
  where a.user_id = p_user_id
    and a.revoked_at is null
    and a.role_code in ('SUPERADMIN', 'ADMIN', 'MODERATOR', 'SUPPORT')
  order by case a.role_code
    when 'SUPERADMIN' then 1 when 'ADMIN' then 2 when 'MODERATOR' then 3 else 4 end
  limit 1;
  select last_sign_in_at into v_last from auth.users where id = p_user_id;
  return jsonb_build_object(
    'user_id', i.user_id,
    'display_name', i.display_name,
    'username', i.username_normalized,
    'role_code', coalesce(v_role, 'USER'),
    'active', i.disabled_at is null,
    'must_change_password', i.must_change_password,
    'is_root', i.is_root,
    'created_at', i.created_at,
    'last_sign_in_at', v_last
  );
end;
$$;

create or replace function public.staff_register_identity(
  p_user_id uuid,
  p_username text,
  p_display_name text,
  p_role_code text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  uname text := lower(btrim(coalesce(p_username, '')));
  role_code text := upper(btrim(coalesce(p_role_code, '')));
  recent integer;
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.manage') then raise exception 'FORBIDDEN'; end if;
  if p_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  if uname !~ '^[a-z0-9._-]{3,64}$' then raise exception 'USERNAME_INVALID'; end if;
  if btrim(coalesce(p_display_name, '')) = '' then raise exception 'NAME_REQUIRED'; end if;
  if role_code not in ('ADMIN', 'MODERATOR', 'SUPPORT') then raise exception 'ROLE_FORBIDDEN'; end if;
  if public._acl_is_protected_admin_root(p_user_id) then raise exception 'ROOT_PROTECTED'; end if;
  select count(*) into recent
  from public.security_audit_events
  where actor_user_id = actor
    and action = 'ADMIN_STAFF_CREATED'
    and occurred_at > timezone('utc', now()) - interval '10 minutes';
  if recent >= 10 then raise exception 'RATE_LIMITED'; end if;
  begin
    insert into public.platform_admin_identities (
      user_id, username_normalized, display_name, must_change_password, is_root
    ) values (
      p_user_id, uname, btrim(p_display_name), true, false
    );
  exception
    when unique_violation then
      raise exception 'USERNAME_TAKEN';
  end;
  insert into public.user_platform_role_assignments (user_id, role_code, granted_by)
  values (p_user_id, role_code, actor)
  on conflict (user_id, role_code) do update
    set revoked_at = null, granted_by = actor, granted_at = timezone('utc', now());
  perform public._canon_admin_audit(
    'ADMIN_STAFF_CREATED', 'platform_admin_identities', p_user_id,
    jsonb_build_object('username', uname, 'role', role_code)
  );
  return jsonb_build_object('ok', true, 'user_id', p_user_id, 'username', uname);
end;
$$;

create or replace function public.staff_set_disabled(p_user_id uuid, p_disabled boolean)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.manage') then raise exception 'FORBIDDEN'; end if;
  if p_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  if public._acl_is_protected_admin_root(p_user_id) then raise exception 'ROOT_PROTECTED'; end if;
  if not exists (select 1 from public.platform_admin_identities where user_id = p_user_id) then
    raise exception 'STAFF_NOT_FOUND';
  end if;
  update public.platform_admin_identities
    set disabled_at = case when p_disabled then timezone('utc', now()) else null end,
        updated_at = timezone('utc', now())
    where user_id = p_user_id;
  perform public._canon_admin_audit(
    case when p_disabled then 'ADMIN_STAFF_DISABLED' else 'ADMIN_STAFF_ENABLED' end,
    'platform_admin_identities', p_user_id,
    jsonb_build_object('disabled', p_disabled)
  );
  return jsonb_build_object('ok', true, 'active', not p_disabled);
end;
$$;

create or replace function public.staff_set_role(p_user_id uuid, p_role_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  role_code text := upper(btrim(coalesce(p_role_code, '')));
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.manage') then raise exception 'FORBIDDEN'; end if;
  if p_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  if public._acl_is_protected_admin_root(p_user_id) then raise exception 'ROOT_PROTECTED'; end if;
  if public._acl_platform_role(p_user_id, 'SUPERADMIN') then raise exception 'ROLE_FORBIDDEN'; end if;
  if role_code not in ('ADMIN', 'MODERATOR', 'SUPPORT') then raise exception 'ROLE_FORBIDDEN'; end if;
  if not exists (select 1 from public.platform_admin_identities where user_id = p_user_id) then
    raise exception 'STAFF_NOT_FOUND';
  end if;
  update public.user_platform_role_assignments
    set revoked_at = timezone('utc', now())
    where user_id = p_user_id
      and role_code in ('ADMIN', 'MODERATOR', 'SUPPORT')
      and revoked_at is null;
  insert into public.user_platform_role_assignments (user_id, role_code, granted_by)
  values (p_user_id, role_code, actor)
  on conflict (user_id, role_code) do update
    set revoked_at = null, granted_by = actor, granted_at = timezone('utc', now());
  perform public._canon_admin_audit(
    'ADMIN_STAFF_ROLE_CHANGED', 'user_platform_role_assignments', p_user_id,
    jsonb_build_object('role', role_code)
  );
  return jsonb_build_object('ok', true, 'role', role_code);
end;
$$;

create or replace function public.staff_force_password_change(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.manage') then raise exception 'FORBIDDEN'; end if;
  if p_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  if public._acl_is_protected_admin_root(p_user_id) then raise exception 'ROOT_PROTECTED'; end if;
  update public.platform_admin_identities
    set must_change_password = true, updated_at = timezone('utc', now())
    where user_id = p_user_id;
  if not found then raise exception 'STAFF_NOT_FOUND'; end if;
  perform public._canon_admin_audit(
    'ADMIN_STAFF_FORCE_PASSWORD_CHANGE', 'platform_admin_identities', p_user_id,
    '{}'::jsonb
  );
  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.staff_on_password_reset(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.manage') then raise exception 'FORBIDDEN'; end if;
  if p_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  if public._acl_is_protected_admin_root(p_user_id) then raise exception 'ROOT_PROTECTED'; end if;
  update public.platform_admin_identities
    set must_change_password = true, updated_at = timezone('utc', now())
    where user_id = p_user_id;
  if not found then raise exception 'STAFF_NOT_FOUND'; end if;
  perform public._canon_admin_audit(
    'ADMIN_STAFF_PASSWORD_RESET', 'platform_admin_identities', p_user_id,
    '{}'::jsonb
  );
  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- Catalogs: permission-gated mutations + audit + service categories
-- ---------------------------------------------------------------------------
create or replace function public.canon_admin_list_species()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.has_permission('catalogs.view') then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'code', s.code, 'name', s.name, 'sort_key', s.sort_key, 'active', s.active
    ) order by s.sort_key, s.name)
    from public.species s
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_upsert_species(
  p_code text, p_name text, p_sort_key integer, p_active boolean
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text;
  v_prev boolean;
begin
  if not public.has_permission('catalogs.manage') then raise exception 'FORBIDDEN'; end if;
  if p_code is null or length(trim(p_code)) = 0 then raise exception 'VALIDATION'; end if;
  v_code := upper(trim(p_code));
  select active into v_prev from public.species where code = v_code;
  insert into public.species (code, name, sort_key, active)
  values (v_code, trim(p_name), coalesce(p_sort_key, 0), coalesce(p_active, true))
  on conflict (code) do update
    set name = excluded.name,
        sort_key = excluded.sort_key,
        active = excluded.active;
  perform public._canon_admin_audit(
    case
      when v_prev is null then 'CATALOG_ITEM_CREATED'
      when v_prev is distinct from coalesce(p_active, true) and coalesce(p_active, true)
        then 'CATALOG_ITEM_ENABLED'
      when v_prev is distinct from coalesce(p_active, true) and not coalesce(p_active, true)
        then 'CATALOG_ITEM_DISABLED'
      else 'CATALOG_ITEM_UPDATED'
    end,
    'species', null,
    jsonb_build_object('catalog', 'species', 'code', v_code, 'active', coalesce(p_active, true))
  );
  return v_code;
end;
$$;

create or replace function public.canon_admin_list_breeds(p_species_code text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.has_permission('catalogs.view') then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', b.id, 'species_code', b.species_code, 'name', b.name,
      'sort_key', b.sort_key, 'active', b.active
    ) order by b.species_code, b.sort_key, b.name)
    from public.breeds b
    where p_species_code is null or b.species_code = p_species_code
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_upsert_breed(
  p_id uuid, p_species_code text, p_name text, p_sort_key integer, p_active boolean
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_prev boolean;
begin
  if not public.has_permission('catalogs.manage') then raise exception 'FORBIDDEN'; end if;
  if p_species_code is null or length(trim(p_name)) = 0 then raise exception 'VALIDATION'; end if;
  if exists (
    select 1 from public.breeds b
    where b.species_code = p_species_code
      and lower(b.name) = lower(trim(p_name))
      and b.active
      and (p_id is null or b.id <> p_id)
  ) then
    raise exception 'DUPLICATE';
  end if;
  if p_id is not null then
    select active into v_prev from public.breeds where id = p_id;
  end if;
  if p_id is null then
    insert into public.breeds (species_code, name, sort_key, active)
    values (p_species_code, trim(p_name), coalesce(p_sort_key, 0), coalesce(p_active, true))
    on conflict (species_code, name) do update
      set sort_key = excluded.sort_key,
          active = excluded.active
    returning id into v_id;
  else
    update public.breeds
      set species_code = p_species_code,
          name = trim(p_name),
          sort_key = coalesce(p_sort_key, sort_key),
          active = coalesce(p_active, active)
    where id = p_id
    returning id into v_id;
  end if;
  perform public._canon_admin_audit(
    case
      when v_prev is null then 'CATALOG_ITEM_CREATED'
      when v_prev is distinct from coalesce(p_active, true) and coalesce(p_active, true)
        then 'CATALOG_ITEM_ENABLED'
      when v_prev is distinct from coalesce(p_active, true) and not coalesce(p_active, true)
        then 'CATALOG_ITEM_DISABLED'
      else 'CATALOG_ITEM_UPDATED'
    end,
    'breeds', v_id,
    jsonb_build_object('catalog', 'breeds', 'species', p_species_code, 'name', trim(p_name))
  );
  return v_id;
end;
$$;

create or replace function public.canon_admin_list_pet_health_products(p_kind text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.has_permission('catalogs.view') then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', p.id, 'kind', p.kind, 'code', p.code, 'display_name', p.display_name,
      'species_code', p.species_code, 'active', p.active, 'sort_order', p.sort_order
    ) order by p.kind, p.sort_order, p.display_name)
    from public.pet_health_products p
    where p_kind is null or p.kind = p_kind
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_upsert_pet_health_product(
  p_id uuid, p_kind text, p_code text, p_display_name text,
  p_species_code text, p_sort_order integer, p_active boolean
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_prev boolean;
begin
  if not public.has_permission('catalogs.manage') then raise exception 'FORBIDDEN'; end if;
  if p_kind not in ('VACCINE', 'FLEA', 'DEWORMER') then raise exception 'VALIDATION'; end if;
  if length(trim(p_code)) = 0 or length(trim(p_display_name)) = 0 then raise exception 'VALIDATION'; end if;
  if p_id is not null then
    select active into v_prev from public.pet_health_products where id = p_id;
  end if;
  if p_id is null then
    insert into public.pet_health_products (kind, code, display_name, species_code, sort_order, active)
    values (p_kind, upper(trim(p_code)), trim(p_display_name), p_species_code, coalesce(p_sort_order, 0), coalesce(p_active, true))
    on conflict (kind, code) do update
      set display_name = excluded.display_name,
          species_code = excluded.species_code,
          sort_order = excluded.sort_order,
          active = excluded.active
    returning id into v_id;
  else
    update public.pet_health_products
      set kind = p_kind,
          code = upper(trim(p_code)),
          display_name = trim(p_display_name),
          species_code = p_species_code,
          sort_order = coalesce(p_sort_order, sort_order),
          active = coalesce(p_active, active)
    where id = p_id
    returning id into v_id;
  end if;
  perform public._canon_admin_audit(
    case
      when v_prev is null then 'CATALOG_ITEM_CREATED'
      when v_prev is distinct from coalesce(p_active, true) and coalesce(p_active, true)
        then 'CATALOG_ITEM_ENABLED'
      when v_prev is distinct from coalesce(p_active, true) and not coalesce(p_active, true)
        then 'CATALOG_ITEM_DISABLED'
      else 'CATALOG_ITEM_UPDATED'
    end,
    'pet_health_products', v_id,
    jsonb_build_object('catalog', 'pet_health_products', 'kind', p_kind, 'code', upper(trim(p_code)))
  );
  return v_id;
end;
$$;

create or replace function public.canon_admin_list_service_categories()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.has_permission('catalogs.view') then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'code', c.code, 'name', c.name, 'sort_key', c.sort_key, 'active', c.active
    ) order by c.sort_key, c.name)
    from public.service_categories c
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_upsert_service_category(
  p_code text, p_name text, p_sort_key integer, p_active boolean
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text;
  v_prev boolean;
begin
  if not public.has_permission('catalogs.manage') then raise exception 'FORBIDDEN'; end if;
  if p_code is null or length(trim(p_code)) = 0 or length(trim(p_name)) = 0 then
    raise exception 'VALIDATION';
  end if;
  v_code := upper(trim(p_code));
  select active into v_prev from public.service_categories where code = v_code;
  insert into public.service_categories (code, name, sort_key, active)
  values (v_code, trim(p_name), coalesce(p_sort_key, 0), coalesce(p_active, true))
  on conflict (code) do update
    set name = excluded.name,
        sort_key = excluded.sort_key,
        active = excluded.active;
  perform public._canon_admin_audit(
    case
      when v_prev is null then 'CATALOG_ITEM_CREATED'
      when v_prev is distinct from coalesce(p_active, true) and coalesce(p_active, true)
        then 'CATALOG_ITEM_ENABLED'
      when v_prev is distinct from coalesce(p_active, true) and not coalesce(p_active, true)
        then 'CATALOG_ITEM_DISABLED'
      else 'CATALOG_ITEM_UPDATED'
    end,
    'service_categories', null,
    jsonb_build_object('catalog', 'service_categories', 'code', v_code)
  );
  return v_code;
end;
$$;

create or replace function public.list_admin_staff_audit(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.view') then raise exception 'FORBIDDEN'; end if;
  if p_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  return coalesce((
    select jsonb_agg(row_to_json(x) order by x.occurred_at desc)
    from (
      select
        e.action,
        e.occurred_at,
        coalesce(i.username_normalized, 'sistema') as actor_username
      from public.security_audit_events e
      left join public.platform_admin_identities i on i.user_id = e.actor_user_id
      where e.entity_id = p_user_id
        and e.action in (
          'ADMIN_STAFF_CREATED',
          'ADMIN_STAFF_DISABLED',
          'ADMIN_STAFF_ENABLED',
          'ADMIN_STAFF_ROLE_CHANGED',
          'ADMIN_STAFF_PASSWORD_RESET',
          'ADMIN_STAFF_FORCE_PASSWORD_CHANGE'
        )
      order by e.occurred_at desc
      limit 50
    ) x
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.list_admin_staff(text) from public, anon;
revoke all on function public.get_admin_staff(uuid) from public, anon;
revoke all on function public.staff_register_identity(uuid, text, text, text) from public, anon;
revoke all on function public.staff_set_disabled(uuid, boolean) from public, anon;
revoke all on function public.staff_set_role(uuid, text) from public, anon;
revoke all on function public.staff_force_password_change(uuid) from public, anon;
revoke all on function public.staff_on_password_reset(uuid) from public, anon;
revoke all on function public.list_admin_staff_audit(uuid) from public, anon;
revoke all on function public.canon_admin_list_service_categories() from public, anon;
revoke all on function public.canon_admin_upsert_service_category(text, text, integer, boolean) from public, anon;

grant execute on function public.list_admin_staff(text) to authenticated;
grant execute on function public.get_admin_staff(uuid) to authenticated;
grant execute on function public.staff_register_identity(uuid, text, text, text) to authenticated;
grant execute on function public.staff_set_disabled(uuid, boolean) to authenticated;
grant execute on function public.staff_set_role(uuid, text) to authenticated;
grant execute on function public.staff_force_password_change(uuid) to authenticated;
grant execute on function public.staff_on_password_reset(uuid) to authenticated;
grant execute on function public.list_admin_staff_audit(uuid) to authenticated;
grant execute on function public.canon_admin_list_species() to authenticated;
grant execute on function public.canon_admin_upsert_species(text, text, integer, boolean) to authenticated;
grant execute on function public.canon_admin_list_breeds(text) to authenticated;
grant execute on function public.canon_admin_upsert_breed(uuid, text, text, integer, boolean) to authenticated;
grant execute on function public.canon_admin_list_pet_health_products(text) to authenticated;
grant execute on function public.canon_admin_upsert_pet_health_product(uuid, text, text, text, text, integer, boolean) to authenticated;
grant execute on function public.canon_admin_list_service_categories() to authenticated;
grant execute on function public.canon_admin_upsert_service_category(text, text, integer, boolean) to authenticated;

notify pgrst, 'reload schema';
