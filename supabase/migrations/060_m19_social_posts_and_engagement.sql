-- =============================================================================
-- LeoVer M19 — migración 060: publicaciones sociales, comentarios, reacciones,
-- RLS y superficie pública sanitizada.
-- Forward-only sobre 001–059. Sin mensajería privada ni feed algorítmico avanzado.
-- LOCAL ONLY — no aplicar hasta entorno no productivo autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos M03 social.* (deny-by-default vía has_org_permission)
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('social.view', 'Ver publicaciones sociales de la organización'),
  ('social.manage', 'Gestionar publicaciones, comentarios y moderación org')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code in ('social.view', 'social.manage')
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code = 'social.view'
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.m19_social_posts (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete restrict,
  author_user_id uuid not null references public.users (id) on delete restrict,
  author_display_name text not null default 'Equipo',
  title text not null,
  content text not null,
  post_status text not null default 'DRAFT',
  cover_image_ref text,
  moderation_status text,
  published_at timestamptz,
  created_by uuid references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m19_post_status_chk check (post_status = any (array[
    'DRAFT','PUBLISHED','HIDDEN','REMOVED'
  ]::text[])),
  constraint m19_post_title_len check (char_length(trim(title)) between 1 and 120),
  constraint m19_post_content_len check (char_length(trim(content)) between 5 and 5000),
  constraint m19_post_author_len check (char_length(trim(author_display_name)) <= 80),
  constraint m19_post_moderation_chk check (
    moderation_status is null
    or moderation_status = any (array['APPROVED','PENDING','BLOCKED','HIDDEN']::text[])
  )
);

create index if not exists m19_posts_org_idx on public.m19_social_posts (organization_id);
create index if not exists m19_posts_status_idx on public.m19_social_posts (post_status);
create index if not exists m19_posts_public_idx
  on public.m19_social_posts (post_status, moderation_status, published_at desc nulls last)
  where post_status = 'PUBLISHED';

create table if not exists public.m19_post_comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.m19_social_posts (id) on delete cascade,
  user_id uuid not null references public.users (id) on delete restrict,
  author_display_name text not null default 'Participante',
  content text not null,
  hidden boolean not null default false,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m19_comment_len check (char_length(trim(content)) between 1 and 1000),
  constraint m19_comment_author_len check (char_length(trim(author_display_name)) <= 80)
);

create index if not exists m19_comments_post_idx
  on public.m19_post_comments (post_id, hidden, created_at);

create table if not exists public.m19_post_reactions (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.m19_social_posts (id) on delete cascade,
  user_id uuid not null references public.users (id) on delete cascade,
  reaction_type text not null,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m19_reaction_type_chk check (reaction_type = any (array[
    'LIKE','SUPPORT','CELEBRATE'
  ]::text[]))
);

create unique index if not exists m19_reaction_post_user_uniq
  on public.m19_post_reactions (post_id, user_id);

create index if not exists m19_reactions_post_idx
  on public.m19_post_reactions (post_id, reaction_type);

-- ---------------------------------------------------------------------------
-- 2. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m19_require_authenticated()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v;
end;
$$;

create or replace function public._m19_org_is_eligible(p_org_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.organizations o
    where o.id = p_org_id
      and o.type in ('SHELTER', 'RESCUE_GROUP', 'NGO', 'TRAINING_CENTER', 'VETERINARY_CLINIC')
      and o.status in ('ACTIVE', 'RESTRICTED')
  );
$$;

create or replace function public._m19_require_org_perm(p_org_id uuid, p_perm text)
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m19_require_authenticated();
begin
  if not public._m19_org_is_eligible(p_org_id) then
    raise exception 'M19_ORGANIZATION_NOT_ELIGIBLE';
  end if;
  if not public.has_org_permission(p_org_id, p_perm) then
    raise exception 'M19_PERMISSION_DENIED';
  end if;
  return v_actor;
end;
$$;

create or replace function public._m19_is_moderator(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_user is not null and (
    public.user_has_active_role(p_user, 'MODERATOR')
    or public.user_has_active_role(p_user, 'ADMIN')
    or public.user_has_active_role(p_user, 'SUPERADMIN')
  );
$$;

create or replace function public._m19_post_is_public(p_row public.m19_social_posts)
returns boolean language sql stable as $$
  select p_row.post_status = 'PUBLISHED'
    and coalesce(p_row.moderation_status, 'APPROVED') in ('APPROVED')
$$;

create or replace function public._m19_engagement_summary(p_post_id uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'like_count', count(*) filter (where r.reaction_type = 'LIKE'),
    'support_count', count(*) filter (where r.reaction_type = 'SUPPORT'),
    'celebrate_count', count(*) filter (where r.reaction_type = 'CELEBRATE'),
    'comment_count', (
      select count(*)::integer from public.m19_post_comments c
      where c.post_id = p_post_id and not c.hidden
    )
  )
  from public.m19_post_reactions r
  where r.post_id = p_post_id;
$$;

create or replace function public._m19_public_post_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  v_org_name text;
  v_eng jsonb;
begin
  select * into v from public.m19_social_posts where id = p_id;
  if not found or not public._m19_post_is_public(v) then return null; end if;

  select coalesce(o.display_name, o.legal_name, 'Organización')
  into v_org_name from public.organizations o where o.id = v.organization_id;

  v_eng := public._m19_engagement_summary(p_id);

  return jsonb_build_object(
    'id', v.id,
    'title', v.title,
    'content', v.content,
    'organization_display_name', v_org_name,
    'author_display_name', v.author_display_name,
    'status', v.post_status,
    'cover_image_ref', v.cover_image_ref,
    'like_count', coalesce((v_eng->>'like_count')::integer, 0),
    'support_count', coalesce((v_eng->>'support_count')::integer, 0),
    'celebrate_count', coalesce((v_eng->>'celebrate_count')::integer, 0),
    'comment_count', coalesce((v_eng->>'comment_count')::integer, 0),
    'published_at', v.published_at,
    'created_at', v.created_at
  );
end;
$$;

create or replace function public._m19_internal_post_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m19_social_posts;
declare v_org_name text;
begin
  select * into v from public.m19_social_posts where id = p_id;
  if not found then return null; end if;

  select coalesce(o.display_name, o.legal_name, 'Organización')
  into v_org_name from public.organizations o where o.id = v.organization_id;

  return jsonb_build_object(
    'id', v.id,
    'organization_id', v.organization_id,
    'organization_display_name', v_org_name,
    'author_user_id', v.author_user_id,
    'author_display_name', v.author_display_name,
    'title', v.title,
    'content', v.content,
    'status', v.post_status,
    'post_status', v.post_status,
    'cover_image_ref', v.cover_image_ref,
    'moderation_status', v.moderation_status,
    'published_at', v.published_at,
    'created_by', v.created_by,
    'created_at', v.created_at,
    'updated_at', v.updated_at
  );
end;
$$;

create or replace function public._m19_public_comment_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare c public.m19_post_comments;
begin
  select * into c from public.m19_post_comments where id = p_id;
  if not found or c.hidden then return null; end if;
  return jsonb_build_object(
    'id', c.id,
    'post_id', c.post_id,
    'author_display_name', c.author_display_name,
    'content', c.content,
    'created_at', c.created_at
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. RLS
-- ---------------------------------------------------------------------------
alter table public.m19_social_posts enable row level security;
alter table public.m19_post_comments enable row level security;
alter table public.m19_post_reactions enable row level security;

create policy m19_posts_select on public.m19_social_posts for select to authenticated
  using (
    public.has_org_permission(organization_id, 'social.view')
    or public._m19_is_moderator(auth.uid())
  );

create policy m19_posts_mut on public.m19_social_posts for all to authenticated
  using (false);

create policy m19_comments_select on public.m19_post_comments for select to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1 from public.m19_social_posts p
      where p.id = post_id
        and public.has_org_permission(p.organization_id, 'social.view')
    )
    or public._m19_is_moderator(auth.uid())
  );

create policy m19_comments_mut on public.m19_post_comments for all to authenticated
  using (false);

create policy m19_reactions_select on public.m19_post_reactions for select to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1 from public.m19_social_posts p
      where p.id = post_id and public._m19_post_is_public(p)
    )
    or public._m19_is_moderator(auth.uid())
  );

create policy m19_reactions_mut on public.m19_post_reactions for all to authenticated
  using (false);

revoke all on table public.m19_social_posts from public, anon;
revoke all on table public.m19_post_comments from public, anon;
revoke all on table public.m19_post_reactions from public, anon;
grant select on table public.m19_social_posts to authenticated;
grant select on table public.m19_post_comments to authenticated;
grant select on table public.m19_post_reactions to authenticated;
grant all on table public.m19_social_posts to service_role;
grant all on table public.m19_post_comments to service_role;
grant all on table public.m19_post_reactions to service_role;

-- ---------------------------------------------------------------------------
-- 4. RPCs — superficie pública (anon + authenticated)
-- ---------------------------------------------------------------------------
create or replace function public.m19_list_public_feed(
  p_query text default null,
  p_organization_id uuid default null,
  p_published_only boolean default true
) returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_row public.m19_social_posts;
  v_q text := nullif(lower(trim(coalesce(p_query, ''))), '');
begin
  for v_row in
    select p.* from public.m19_social_posts p
    where public._m19_post_is_public(p)
      and (not coalesce(p_published_only, true) or p.post_status = 'PUBLISHED')
      and (p_organization_id is null or p.organization_id = p_organization_id)
      and (
        v_q is null
        or p.title ilike '%' || v_q || '%'
        or p.content ilike '%' || v_q || '%'
      )
    order by coalesce(p.published_at, p.created_at) desc
  loop
    return next public._m19_public_post_json(v_row.id);
  end loop;
end;
$$;

create or replace function public.m19_get_public_post(p_post_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  v_json jsonb;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if v.post_status = 'REMOVED' then raise exception 'M19_POST_REMOVED'; end if;
  if v.post_status <> 'PUBLISHED' then raise exception 'M19_POST_NOT_PUBLIC'; end if;
  v_json := public._m19_public_post_json(p_post_id);
  if v_json is null then raise exception 'M19_POST_NOT_PUBLIC'; end if;
  return v_json;
end;
$$;

create or replace function public.m19_list_public_comments(p_post_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  c public.m19_post_comments;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if not public._m19_post_is_public(v) then raise exception 'M19_POST_NOT_PUBLIC'; end if;
  for c in
    select * from public.m19_post_comments
    where post_id = p_post_id and not hidden
    order by created_at asc
  loop
    return next public._m19_public_comment_json(c.id);
  end loop;
end;
$$;

create or replace function public.m19_get_engagement_summary(p_post_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m19_social_posts;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if not public._m19_post_is_public(v) then
    perform public._m19_require_org_perm(v.organization_id, 'social.view');
  end if;
  return public._m19_engagement_summary(p_post_id);
end;
$$;

create or replace function public.m19_is_organization_eligible(p_organization_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public._m19_org_is_eligible(p_organization_id);
$$;

-- ---------------------------------------------------------------------------
-- 5. RPCs — administración e interacción (authenticated)
-- ---------------------------------------------------------------------------
create or replace function public.m19_get_post(p_post_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m19_social_posts;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  perform public._m19_require_org_perm(v.organization_id, 'social.view');
  return public._m19_internal_post_json(p_post_id);
end;
$$;

create or replace function public.m19_list_org_posts(p_organization_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_row public.m19_social_posts;
begin
  perform public._m19_require_org_perm(p_organization_id, 'social.view');
  for v_row in
    select * from public.m19_social_posts
    where organization_id = p_organization_id
    order by coalesce(published_at, created_at) desc
  loop
    return next public._m19_internal_post_json(v_row.id);
  end loop;
end;
$$;

create or replace function public.m19_create_post(
  p_organization_id uuid,
  p_title text,
  p_content text,
  p_cover_image_ref text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid;
  v_id uuid;
  v_org_name text;
begin
  v_actor := public._m19_require_org_perm(p_organization_id, 'social.manage');
  if char_length(trim(coalesce(p_title, ''))) < 1
    or char_length(trim(p_title)) > 120 then
    raise exception 'M19_INVALID_TITLE';
  end if;
  if char_length(trim(coalesce(p_content, ''))) < 5
    or char_length(coalesce(p_content, '')) > 5000 then
    raise exception 'M19_INVALID_CONTENT';
  end if;

  select coalesce(o.display_name, o.legal_name, 'Organización')
  into v_org_name from public.organizations o where o.id = p_organization_id;

  insert into public.m19_social_posts (
    organization_id, author_user_id, author_display_name,
    title, content, post_status, cover_image_ref, created_by
  ) values (
    p_organization_id, v_actor, coalesce(v_org_name, 'Equipo'),
    trim(p_title), trim(p_content), 'DRAFT', p_cover_image_ref, v_actor
  ) returning id into v_id;

  return public._m19_internal_post_json(v_id);
end;
$$;

create or replace function public.m19_update_post(
  p_post_id uuid,
  p_title text,
  p_content text,
  p_cover_image_ref text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m19_social_posts;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if v.post_status = 'REMOVED' then raise exception 'M19_STATE_ALREADY_FINAL'; end if;
  perform public._m19_require_org_perm(v.organization_id, 'social.manage');
  if char_length(trim(coalesce(p_title, ''))) < 1
    or char_length(trim(p_title)) > 120 then
    raise exception 'M19_INVALID_TITLE';
  end if;
  if char_length(trim(coalesce(p_content, ''))) < 5
    or char_length(coalesce(p_content, '')) > 5000 then
    raise exception 'M19_INVALID_CONTENT';
  end if;

  update public.m19_social_posts set
    title = trim(p_title),
    content = trim(p_content),
    cover_image_ref = coalesce(p_cover_image_ref, cover_image_ref),
    updated_at = timezone('utc', now())
  where id = p_post_id;

  return public._m19_internal_post_json(p_post_id);
end;
$$;

create or replace function public.m19_transition_post(
  p_post_id uuid,
  p_target_status text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m19_social_posts;
declare v_target text := upper(trim(p_target_status));
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  perform public._m19_require_org_perm(v.organization_id, 'social.manage');

  if v.post_status = v_target then
    return public._m19_internal_post_json(p_post_id);
  end if;
  if v.post_status = 'REMOVED' then raise exception 'M19_STATE_ALREADY_FINAL'; end if;

  if v_target = 'PUBLISHED' and v.post_status not in ('DRAFT', 'HIDDEN') then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  elsif v_target = 'HIDDEN' and v.post_status <> 'PUBLISHED' then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  elsif v_target = 'REMOVED' and v.post_status = 'REMOVED' then
    raise exception 'M19_STATE_ALREADY_FINAL';
  elsif v_target = 'REMOVED' then
    null;
  elsif v_target not in ('DRAFT','PUBLISHED','HIDDEN','REMOVED') then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  end if;

  update public.m19_social_posts set
    post_status = v_target,
    published_at = case
      when v_target = 'PUBLISHED' and published_at is null then timezone('utc', now())
      else published_at
    end,
    updated_at = timezone('utc', now())
  where id = p_post_id;

  return public._m19_internal_post_json(p_post_id);
end;
$$;

create or replace function public.m19_add_comment(
  p_post_id uuid,
  p_content text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  v_actor uuid := public._m19_require_authenticated();
  v_id uuid;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if not public._m19_post_is_public(v) then raise exception 'M19_POST_NOT_PUBLIC'; end if;
  if char_length(trim(coalesce(p_content, ''))) < 1
    or char_length(coalesce(p_content, '')) > 1000 then
    raise exception 'M19_INVALID_COMMENT';
  end if;

  insert into public.m19_post_comments (
    post_id, user_id, author_display_name, content
  ) values (
    p_post_id, v_actor, 'Participante', trim(p_content)
  ) returning id into v_id;

  return public._m19_public_comment_json(v_id);
end;
$$;

create or replace function public.m19_add_reaction(
  p_post_id uuid,
  p_reaction_type text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  v_actor uuid := public._m19_require_authenticated();
  v_type text := upper(trim(p_reaction_type));
  v_id uuid;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if not public._m19_post_is_public(v) then raise exception 'M19_POST_NOT_PUBLIC'; end if;
  if v_type not in ('LIKE','SUPPORT','CELEBRATE') then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  end if;

  delete from public.m19_post_reactions
  where post_id = p_post_id and user_id = v_actor;

  insert into public.m19_post_reactions (post_id, user_id, reaction_type)
  values (p_post_id, v_actor, v_type)
  returning id into v_id;

  return jsonb_build_object(
    'id', v_id,
    'post_id', p_post_id,
    'user_id', v_actor,
    'reaction_type', v_type,
    'created_at', timezone('utc', now())
  );
end;
$$;

create or replace function public.m19_remove_reaction(p_post_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m19_require_authenticated();
begin
  delete from public.m19_post_reactions
  where post_id = p_post_id and user_id = v_actor;
  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.m19_get_my_reaction(p_post_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m19_require_authenticated();
  r public.m19_post_reactions;
begin
  select * into r from public.m19_post_reactions
  where post_id = p_post_id and user_id = v_actor;
  if not found then return null; end if;
  return jsonb_build_object(
    'id', r.id,
    'post_id', r.post_id,
    'user_id', r.user_id,
    'reaction_type', r.reaction_type,
    'created_at', r.created_at
  );
end;
$$;

-- Grants RPC
grant execute on function public.m19_list_public_feed(text, uuid, boolean) to anon, authenticated;
grant execute on function public.m19_get_public_post(uuid) to anon, authenticated;
grant execute on function public.m19_list_public_comments(uuid) to anon, authenticated;
grant execute on function public.m19_get_engagement_summary(uuid) to anon, authenticated;
grant execute on function public.m19_is_organization_eligible(uuid) to anon, authenticated;
grant execute on function public.m19_get_post(uuid) to authenticated;
grant execute on function public.m19_list_org_posts(uuid) to authenticated;
grant execute on function public.m19_create_post(uuid, text, text, text) to authenticated;
grant execute on function public.m19_update_post(uuid, text, text, text) to authenticated;
grant execute on function public.m19_transition_post(uuid, text) to authenticated;
grant execute on function public.m19_add_comment(uuid, text) to authenticated;
grant execute on function public.m19_add_reaction(uuid, text) to authenticated;
grant execute on function public.m19_remove_reaction(uuid) to authenticated;
grant execute on function public.m19_get_my_reaction(uuid) to authenticated;

commit;
