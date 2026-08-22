-- LeoVer Canonical
-- Logical migration: 1034
-- ORG-01.1 invitations + tutorial_progress RPCs + STORY/REEL distinct from POST.
-- Forward-only. Do not edit 1000-1033. Staging only.

-- ---------------------------------------------------------------------------
-- ORG permission catalog (same family: permission_codes scope ORG)
-- ---------------------------------------------------------------------------

insert into public.permission_codes (code, scope, description) values
  ('org.publish', 'ORG', 'Publish organization content'),
  ('org.schedule.manage', 'ORG', 'Manage schedule'),
  ('org.reservations.manage', 'ORG', 'Manage reservations'),
  ('org.guests.manage', 'ORG', 'Manage guests/stays')
on conflict (code) do nothing;

-- ---------------------------------------------------------------------------
-- Membership granted permissions (MEMBER extras; backend authority)
-- ---------------------------------------------------------------------------

alter table public.organization_memberships
  add column if not exists granted_permissions text[] not null default '{}';

alter table public.organization_invitations
  add column if not exists permission_codes text[] not null default '{}';

alter table public.organization_invitations
  drop constraint if exists organization_invitations_status_check;

alter table public.organization_invitations
  add constraint organization_invitations_status_check
  check (status in ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED', 'REJECTED'));

create unique index if not exists organization_invitations_pending_person_uidx
  on public.organization_invitations (organization_id, invitee_user_id)
  where status = 'PENDING' and invitee_user_id is not null;

-- Last-admin helper
create or replace function public._canon_active_admin_count(p_org uuid)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select count(*)::integer
  from public.organization_memberships m
  join public.organization_roles r on r.id = m.role_id
  where m.organization_id = p_org
    and m.status = 'ACTIVE'
    and r.code in ('OWNER', 'ADMIN');
$$;

create or replace function public._acl_org_permission(p_user_id uuid, p_org_id uuid, p_code text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.organization_memberships m
    join public.organization_roles r on r.id = m.role_id
    left join public.organization_role_permissions rp on rp.role_id = m.role_id and rp.permission_code = p_code
    where m.person_id = p_user_id
      and m.organization_id = p_org_id
      and m.status = 'ACTIVE'
      and (
        rp.permission_code is not null
        or p_code = any (coalesce(m.granted_permissions, '{}'))
      )
  );
$$;

-- ---------------------------------------------------------------------------
-- Person search (username / display name). No emails, no technical IDs in result.
-- ---------------------------------------------------------------------------

create or replace function public.canon_search_persons(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare v_q text := btrim(coalesce(p_query, ''));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if char_length(v_q) < 2 then return '[]'::jsonb; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'user_id', p.user_id,
      'username', p.username,
      'display_name', p.display_name,
      'avatar_asset_id', p.avatar_asset_id
    ) order by p.display_name)
    from (
      select p.user_id, p.username, p.display_name, p.avatar_asset_id
      from public.persons p
      where p.lifecycle_status = 'ACTIVE'
        and p.user_id <> auth.uid()
        and (
          p.username ilike ('%' || v_q || '%')
          or p.display_name ilike ('%' || v_q || '%')
        )
      order by p.display_name
      limit 20
    ) p
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Invite / list / accept / reject
-- ---------------------------------------------------------------------------

create or replace function public._canon_ensure_org_role(p_org uuid, p_code text, p_name text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  select id into v_id
  from public.organization_roles
  where organization_id = p_org and code = p_code;
  if v_id is not null then return v_id; end if;
  insert into public.organization_roles (organization_id, code, name, is_system)
  values (p_org, p_code, p_name, true)
  returning id into v_id;
  if p_code in ('OWNER', 'ADMIN') then
    insert into public.organization_role_permissions (role_id, permission_code)
    select v_id, code from public.permission_codes where scope = 'ORG'
    on conflict do nothing;
  else
    insert into public.organization_role_permissions (role_id, permission_code)
    values (v_id, 'org.view')
    on conflict do nothing;
  end if;
  return v_id;
end;
$$;

create or replace function public.canon_invite_organization_member(
  p_organization_id uuid,
  p_invitee_user_id uuid,
  p_role_code text,
  p_permission_codes text[] default '{}'
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text := upper(btrim(coalesce(p_role_code, 'MEMBER')));
  v_id uuid;
  v_role_id uuid;
  v_org_name text;
  v_inviter text;
  v_perms text[];
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_organization_id is null or p_invitee_user_id is null then
    raise exception 'INVITE_TARGET_REQUIRED';
  end if;
  if p_invitee_user_id = auth.uid() then raise exception 'CANNOT_INVITE_SELF'; end if;
  if not public._acl_org_permission(auth.uid(), p_organization_id, 'org.members.manage') then
    raise exception 'FORBIDDEN';
  end if;
  if v_role not in ('ADMIN', 'MEMBER') then raise exception 'ROLE_NOT_INVITABLE'; end if;
  if not exists (select 1 from public.persons where user_id = p_invitee_user_id and lifecycle_status = 'ACTIVE') then
    raise exception 'PERSON_NOT_FOUND';
  end if;
  if exists (
    select 1 from public.organization_memberships
    where organization_id = p_organization_id
      and person_id = p_invitee_user_id
      and status = 'ACTIVE'
  ) then
    raise exception 'ALREADY_MEMBER';
  end if;
  if exists (
    select 1 from public.organization_invitations
    where organization_id = p_organization_id
      and invitee_user_id = p_invitee_user_id
      and status = 'PENDING'
  ) then
    raise exception 'PENDING_INVITE_EXISTS';
  end if;

  v_role_id := public._canon_ensure_org_role(
    p_organization_id,
    v_role,
    case when v_role = 'ADMIN' then 'Administrador' else 'Miembro' end
  );
  v_perms := case
    when v_role = 'ADMIN' then array(select code from public.permission_codes where scope = 'ORG')
    else array(
      select distinct x from unnest(coalesce(p_permission_codes, '{}')) as x
      where x in (select code from public.permission_codes where scope = 'ORG')
    )
  end;
  if v_role = 'MEMBER' and not ('org.view' = any (v_perms)) then
    v_perms := array_append(v_perms, 'org.view');
  end if;

  insert into public.organization_invitations (
    organization_id, invitee_user_id, role_id, invited_by, status, expires_at, permission_codes
  ) values (
    p_organization_id, p_invitee_user_id, v_role_id, auth.uid(), 'PENDING',
    timezone('utc', now()) + interval '7 days', v_perms
  )
  returning id into v_id;

  select o.name, per.display_name into v_org_name, v_inviter
  from public.organizations o
  join public.persons per on per.user_id = auth.uid()
  where o.id = p_organization_id;

  insert into public.notifications (user_id, kind, payload)
  values (
    p_invitee_user_id,
    'ORG_INVITE',
    jsonb_build_object(
      'invitation_id', v_id,
      'organization_id', p_organization_id,
      'organization_name', coalesce(v_org_name, 'Organización'),
      'inviter_name', coalesce(v_inviter, ''),
      'role_code', v_role,
      'permission_codes', to_jsonb(v_perms)
    )
  );

  perform public.canon_audit(
    'org.invite',
    'organization_invitations',
    v_id,
    jsonb_build_object('invitee', p_invitee_user_id, 'role', v_role, 'actor', auth.uid())
  );
  return v_id;
end;
$$;

create or replace function public.canon_list_org_invitations(p_organization_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_org_permission(auth.uid(), p_organization_id, 'org.members.manage') then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', i.id,
      'organization_id', i.organization_id,
      'invitee_user_id', i.invitee_user_id,
      'invitee_name', per.display_name,
      'invitee_username', per.username,
      'role_code', r.code,
      'status', i.status,
      'permission_codes', i.permission_codes,
      'invited_by', i.invited_by,
      'expires_at', i.expires_at,
      'created_at', i.created_at
    ) order by i.created_at desc)
    from public.organization_invitations i
    join public.organization_roles r on r.id = i.role_id
    left join public.persons per on per.user_id = i.invitee_user_id
    where i.organization_id = p_organization_id
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_my_org_invitations()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', i.id,
      'organization_id', i.organization_id,
      'organization_name', o.name,
      'organization_capability', (
        select oc.capability from public.organization_capabilities oc
        where oc.organization_id = i.organization_id
        limit 1
      ),
      'invitee_user_id', i.invitee_user_id,
      'role_code', r.code,
      'status', i.status,
      'permission_codes', i.permission_codes,
      'invited_by', i.invited_by,
      'inviter_name', inv.display_name,
      'expires_at', i.expires_at,
      'created_at', i.created_at
    ) order by i.created_at desc)
    from public.organization_invitations i
    join public.organizations o on o.id = i.organization_id
    join public.organization_roles r on r.id = i.role_id
    left join public.persons inv on inv.user_id = i.invited_by
    where i.invitee_user_id = auth.uid()
      and i.status = 'PENDING'
      and i.expires_at > timezone('utc', now())
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_accept_org_invitation(p_invitation_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_inv public.organization_invitations;
  v_role text;
  v_membership uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_inv from public.organization_invitations where id = p_invitation_id;
  if not found then raise exception 'INVITATION_NOT_FOUND'; end if;
  if v_inv.invitee_user_id is distinct from auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_inv.status = 'ACCEPTED' then
    select id into v_membership from public.organization_memberships
    where organization_id = v_inv.organization_id and person_id = auth.uid() and status = 'ACTIVE'
    limit 1;
    if v_membership is not null then return v_membership; end if;
  end if;
  if v_inv.status <> 'PENDING' then raise exception 'INVITATION_NOT_PENDING'; end if;
  if v_inv.expires_at <= timezone('utc', now()) then
    update public.organization_invitations set status = 'EXPIRED' where id = p_invitation_id;
    raise exception 'EXPIRED';
  end if;
  select code into v_role from public.organization_roles where id = v_inv.role_id;
  select id into v_membership from public.organization_memberships
  where organization_id = v_inv.organization_id and person_id = auth.uid() and status = 'ACTIVE'
  limit 1;
  if v_membership is null then
    insert into public.organization_memberships (
      organization_id, person_id, role_id, status, granted_permissions
    ) values (
      v_inv.organization_id, auth.uid(), v_inv.role_id, 'ACTIVE', coalesce(v_inv.permission_codes, '{}')
    )
    returning id into v_membership;
  end if;
  update public.organization_invitations
    set status = 'ACCEPTED', accepted_at = timezone('utc', now())
    where id = p_invitation_id;
  perform public.canon_audit('org.invite.accept', 'organization_memberships', v_membership,
    jsonb_build_object('invitation_id', p_invitation_id, 'actor', auth.uid()));
  return v_membership;
end;
$$;

create or replace function public.canon_reject_org_invitation(p_invitation_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare v_inv public.organization_invitations;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_inv from public.organization_invitations where id = p_invitation_id;
  if not found then raise exception 'INVITATION_NOT_FOUND'; end if;
  if v_inv.invitee_user_id is distinct from auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_inv.status in ('REJECTED', 'REVOKED', 'EXPIRED') then return; end if;
  if v_inv.status <> 'PENDING' then raise exception 'INVITATION_NOT_PENDING'; end if;
  update public.organization_invitations set status = 'REJECTED' where id = p_invitation_id;
  perform public.canon_audit('org.invite.reject', 'organization_invitations', p_invitation_id,
    jsonb_build_object('actor', auth.uid()));
end;
$$;

-- ---------------------------------------------------------------------------
-- Tutorial progress (reuse existing table)
-- ---------------------------------------------------------------------------

alter table public.tutorial_progress
  add column if not exists state text;

update public.tutorial_progress
set state = case
  when completed_at is not null then 'COMPLETED'
  when skipped_at is not null then 'SKIPPED'
  when viewed_at is not null then 'VIEWED'
  else 'NOT_SEEN'
end
where state is null;

alter table public.tutorial_progress enable row level security;
drop policy if exists tutorial_progress_own on public.tutorial_progress;
create policy tutorial_progress_own on public.tutorial_progress
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create or replace function public.canon_upsert_tutorial_progress(
  p_tutorial_key text,
  p_version text,
  p_state text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare v_state text := upper(btrim(coalesce(p_state, 'VIEWED')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_state not in ('NOT_SEEN', 'VIEWED', 'COMPLETED', 'SKIPPED') then
    raise exception 'TUTORIAL_STATE_INVALID';
  end if;
  insert into public.tutorial_progress (
    user_id, tutorial_key, version, state, viewed_at, completed_at, skipped_at, updated_at
  ) values (
    auth.uid(), p_tutorial_key, coalesce(nullif(p_version, ''), '1'), v_state,
    case when v_state in ('VIEWED', 'COMPLETED', 'SKIPPED') then timezone('utc', now()) else null end,
    case when v_state = 'COMPLETED' then timezone('utc', now()) else null end,
    case when v_state = 'SKIPPED' then timezone('utc', now()) else null end,
    timezone('utc', now())
  )
  on conflict (user_id, tutorial_key) do update
    set version = excluded.version,
        state = excluded.state,
        viewed_at = coalesce(public.tutorial_progress.viewed_at, excluded.viewed_at),
        completed_at = coalesce(public.tutorial_progress.completed_at, excluded.completed_at),
        skipped_at = coalesce(public.tutorial_progress.skipped_at, excluded.skipped_at),
        updated_at = timezone('utc', now());
end;
$$;

create or replace function public.canon_list_tutorial_progress()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'tutorial_key', tutorial_key,
      'version', version,
      'state', coalesce(state,
        case
          when completed_at is not null then 'COMPLETED'
          when skipped_at is not null then 'SKIPPED'
          when viewed_at is not null then 'VIEWED'
          else 'NOT_SEEN'
        end),
      'viewed_at', viewed_at,
      'completed_at', completed_at,
      'skipped_at', skipped_at
    ))
    from public.tutorial_progress
    where user_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Social: STORY table extension, POST content_kind, comments list
-- ---------------------------------------------------------------------------

alter table public.social_posts
  add column if not exists content_kind text not null default 'POST';

alter table public.social_posts
  drop constraint if exists social_posts_content_kind_check;

alter table public.social_posts
  add constraint social_posts_content_kind_check
  check (content_kind in ('POST', 'REEL'));

alter table public.social_posts
  add column if not exists media_asset_id uuid references public.media_assets(id);

alter table public.social_posts
  add column if not exists pet_id uuid references public.pets(id);

alter table public.social_posts
  add column if not exists locality_id text references public.location_nodes(id);

alter table public.social_posts
  add column if not exists composition jsonb not null default '{}'::jsonb;

alter table public.social_stories
  add column if not exists pet_id uuid references public.pets(id);

alter table public.social_stories
  add column if not exists locality_id text references public.location_nodes(id);

alter table public.social_stories
  add column if not exists composition jsonb not null default '{}'::jsonb;

alter table public.social_stories
  add column if not exists caption text;

alter table public.social_comments
  add column if not exists parent_id uuid references public.social_comments(id);

drop function if exists public.canon_create_social_post(text, text);

create or replace function public.canon_create_social_post(
  p_body text,
  p_visibility text default 'PUBLIC',
  p_content_kind text default 'POST',
  p_media_asset_id uuid default null,
  p_pet_id uuid default null,
  p_locality_id text default null,
  p_composition jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
declare v_kind text := upper(btrim(coalesce(p_content_kind, 'POST')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_visibility not in ('PUBLIC', 'FOLLOWERS', 'PRIVATE') then
    raise exception 'VISIBILITY_INVALID';
  end if;
  if v_kind not in ('POST', 'REEL') then raise exception 'CONTENT_KIND_INVALID'; end if;
  if public.person_is_under_13((select birth_date from public.persons where user_id = auth.uid())) then
    raise exception 'UNDER_13_DENIED';
  end if;
  if v_kind = 'REEL' and p_media_asset_id is null then
    raise exception 'MEDIA_REQUIRED';
  end if;
  insert into public.social_posts (
    author_user_id, body, visibility, content_kind, media_asset_id, pet_id, locality_id, composition
  ) values (
    auth.uid(), p_body, p_visibility, v_kind, p_media_asset_id, p_pet_id, p_locality_id,
    coalesce(p_composition, '{}'::jsonb)
  )
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_list_social_feed()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', s.id,
      'author_user_id', s.author_user_id,
      'author_name', per.display_name,
      'author_username', per.username,
      'body', s.body,
      'visibility', s.visibility,
      'content_kind', s.content_kind,
      'sponsored', s.sponsored,
      'media_asset_id', s.media_asset_id,
      'media_bucket', ma.bucket,
      'media_path', ma.object_path,
      'media_mime', ma.mime_type,
      'pet_id', s.pet_id,
      'locality_id', s.locality_id,
      'like_count', (select count(*) from public.social_reactions r where r.post_id = s.id),
      'comment_count', (select count(*) from public.social_comments c where c.post_id = s.id and c.hidden_at is null),
      'created_at', s.created_at
    ) order by s.created_at desc)
    from public.social_posts s
    join public.persons per on per.user_id = s.author_user_id
    left join public.media_assets ma on ma.id = s.media_asset_id
    where s.hidden_at is null
      and s.visibility = 'PUBLIC'
      and s.sponsored = false
      and s.content_kind in ('POST', 'REEL')
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_create_story(
  p_asset_id uuid,
  p_caption text default null,
  p_pet_id uuid default null,
  p_locality_id text default null,
  p_composition jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_asset_id is null then raise exception 'MEDIA_REQUIRED'; end if;
  insert into public.social_stories (
    author_user_id, asset_id, expires_at, pet_id, locality_id, composition, caption
  ) values (
    auth.uid(), p_asset_id, timezone('utc', now()) + interval '24 hours',
    p_pet_id, p_locality_id, coalesce(p_composition, '{}'::jsonb), p_caption
  )
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_list_active_stories()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', s.id,
      'author_user_id', s.author_user_id,
      'author_name', per.display_name,
      'author_username', per.username,
      'author_avatar_asset_id', per.avatar_asset_id,
      'asset_id', s.asset_id,
      'media_bucket', ma.bucket,
      'media_path', ma.object_path,
      'media_mime', ma.mime_type,
      'caption', s.caption,
      'pet_id', s.pet_id,
      'locality_id', s.locality_id,
      'composition', s.composition,
      'created_at', s.created_at,
      'expires_at', s.expires_at
    ) order by s.created_at desc)
    from public.social_stories s
    join public.persons per on per.user_id = s.author_user_id
    left join public.media_assets ma on ma.id = s.asset_id
    where s.hidden_at is null
      and s.expires_at > timezone('utc', now())
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_social_comments(p_post_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id,
      'post_id', c.post_id,
      'author_user_id', c.author_user_id,
      'author_name', per.display_name,
      'author_avatar_asset_id', per.avatar_asset_id,
      'body', c.body,
      'parent_id', c.parent_id,
      'created_at', c.created_at
    ) order by c.created_at asc)
    from public.social_comments c
    join public.persons per on per.user_id = c.author_user_id
    where c.post_id = p_post_id and c.hidden_at is null
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_delete_own_comment(p_comment_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  update public.social_comments
    set hidden_at = timezone('utc', now())
    where id = p_comment_id and author_user_id = auth.uid() and hidden_at is null;
end;
$$;

create or replace function public.canon_list_my_notifications()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', n.id,
      'kind', n.kind,
      'payload', n.payload,
      'read_at', n.read_at,
      'created_at', n.created_at
    ) order by n.created_at desc)
    from public.notifications n
    where n.user_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_search_persons(text) to authenticated;
grant execute on function public.canon_invite_organization_member(uuid, uuid, text, text[]) to authenticated;
grant execute on function public.canon_list_org_invitations(uuid) to authenticated;
grant execute on function public.canon_list_my_org_invitations() to authenticated;
grant execute on function public.canon_accept_org_invitation(uuid) to authenticated;
grant execute on function public.canon_reject_org_invitation(uuid) to authenticated;
grant execute on function public.canon_upsert_tutorial_progress(text, text, text) to authenticated;
grant execute on function public.canon_list_tutorial_progress() to authenticated;
grant execute on function public.canon_create_story(uuid, text, uuid, text, jsonb) to authenticated;
grant execute on function public.canon_list_active_stories() to authenticated;
grant execute on function public.canon_list_social_comments(uuid) to authenticated;
grant execute on function public.canon_delete_own_comment(uuid) to authenticated;
grant execute on function public.canon_list_my_notifications() to authenticated;
