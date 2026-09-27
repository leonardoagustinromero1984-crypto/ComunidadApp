-- 1063: SEC-03 canonical rate limits, quotas, kill switches.
-- STAGING defaults only. Does not edit 1057–1062.
-- Does not change approved functional behavior except when a security limit is exceeded.
-- Authority is server-side. Clients cannot raise their own quota.

create table if not exists public.security_rate_limit_policies (
  operation_key text primary key,
  window_seconds integer not null check (window_seconds >= 60),
  limit_count bigint not null check (limit_count > 0),
  scope_kind text not null check (scope_kind in ('USER', 'ADMIN', 'ORGANIZATION', 'GLOBAL')),
  enabled boolean not null default true,
  fail_closed boolean not null default false,
  exceed_code text not null default 'RATE_LIMITED'
    check (exceed_code in ('RATE_LIMITED', 'QUOTA_EXCEEDED')),
  notes text not null default '',
  updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.security_usage_windows (
  operation_key text not null,
  scope_kind text not null,
  scope_id text not null,
  window_started_at timestamptz not null,
  used_count bigint not null default 0,
  used_units bigint not null default 0,
  primary key (operation_key, scope_kind, scope_id, window_started_at)
);

create table if not exists public.security_feature_flags (
  flag_key text primary key,
  enabled boolean not null default true,
  notes text not null default '',
  updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.security_rate_limit_rpc_bindings (
  proname text primary key,
  operation_key text not null references public.security_rate_limit_policies(operation_key)
);

alter table public.security_rate_limit_policies enable row level security;
alter table public.security_usage_windows enable row level security;
alter table public.security_feature_flags enable row level security;
alter table public.security_rate_limit_rpc_bindings enable row level security;

revoke all on public.security_rate_limit_policies from public, anon, authenticated;
revoke all on public.security_usage_windows from public, anon, authenticated;
revoke all on public.security_feature_flags from public, anon, authenticated;
revoke all on public.security_rate_limit_rpc_bindings from public, anon, authenticated;

insert into public.security_feature_flags (flag_key, enabled, notes) values
  ('media.video.upload.enabled', true, 'Kill switch for video uploads'),
  ('reels.create.enabled', true, 'Kill switch for reel create'),
  ('imports.enabled', true, 'Kill switch for VitaCora import analyze/execute')
on conflict (flag_key) do nothing;

-- Conservative STAGING defaults. Not a production commercial policy.
insert into public.security_rate_limit_policies
  (operation_key, window_seconds, limit_count, scope_kind, enabled, fail_closed, exceed_code, notes)
values
  ('social.post.create', 3600, 10, 'USER', true, false, 'RATE_LIMITED', 'Human posting; stop bursts'),
  ('social.reel.create', 3600, 6, 'USER', true, false, 'RATE_LIMITED', 'Video cost companion'),
  ('social.story.create', 3600, 12, 'USER', true, false, 'RATE_LIMITED', 'Story bursts'),
  ('social.comment.create', 600, 30, 'USER', true, false, 'RATE_LIMITED', 'Comment spam'),
  ('social.reaction', 600, 60, 'USER', true, false, 'RATE_LIMITED', 'Reaction storms'),
  ('social.connection_request', 3600, 20, 'USER', true, false, 'RATE_LIMITED', 'Account farming'),
  ('social.connection_cancel', 3600, 20, 'USER', true, false, 'RATE_LIMITED', 'Request churn'),
  ('chat.message.send', 60, 40, 'USER', true, false, 'RATE_LIMITED', 'Per-minute chat'),
  ('chat.conversation.create', 3600, 15, 'USER', true, false, 'RATE_LIMITED', 'Conversation farming'),
  ('report.create', 3600, 15, 'USER', true, false, 'RATE_LIMITED', 'Automated reports'),
  ('social.lost_found.create', 3600, 8, 'USER', true, false, 'RATE_LIMITED', 'Lost/found spam'),
  ('social.org_invite', 3600, 20, 'USER', true, false, 'RATE_LIMITED', 'Invite + notification spam'),
  ('media.register', 3600, 30, 'USER', true, true, 'RATE_LIMITED', 'Metadata + storage insert'),
  ('media.upload.bytes.daily', 86400, 209715200, 'USER', true, true, 'QUOTA_EXCEEDED', '200 MiB/day declared size'),
  ('media.video.count.daily', 86400, 8, 'USER', true, true, 'QUOTA_EXCEEDED', 'Daily video count'),
  ('signed_url.request', 600, 60, 'USER', true, true, 'RATE_LIMITED', 'Private signed URL gate'),
  ('vitacora.import.analyze', 3600, 6, 'USER', true, true, 'RATE_LIMITED', 'XLSX analyze'),
  ('vitacora.import.execute', 3600, 4, 'USER', true, true, 'RATE_LIMITED', 'Import confirm'),
  ('admin.staff.create', 600, 10, 'ADMIN', true, true, 'RATE_LIMITED', 'Staff create'),
  ('admin.password_reset', 600, 10, 'ADMIN', true, true, 'RATE_LIMITED', 'Staff password reset'),
  ('admin.role_change', 3600, 20, 'ADMIN', true, true, 'RATE_LIMITED', 'Role mutations')
on conflict (operation_key) do nothing;

insert into public.security_rate_limit_rpc_bindings (proname, operation_key) values
  ('canon_create_story', 'social.story.create'),
  ('canon_comment_social_post', 'social.comment.create'),
  ('canon_comment_story', 'social.comment.create'),
  ('canon_react_social_post', 'social.reaction'),
  ('canon_react_story', 'social.reaction'),
  ('canon_send_friend_request', 'social.connection_request'),
  ('canon_cancel_friend_request', 'social.connection_cancel'),
  ('canon_start_conversation', 'chat.conversation.create'),
  ('canon_send_message', 'chat.message.send'),
  ('create_content_report', 'report.create'),
  ('canon_create_lost_found', 'social.lost_found.create'),
  ('canon_invite_organization_member', 'social.org_invite'),
  ('canon_import_analyze', 'vitacora.import.analyze'),
  ('canon_import_confirm', 'vitacora.import.execute'),
  ('staff_register_identity', 'admin.staff.create'),
  ('staff_force_password_change', 'admin.password_reset'),
  ('staff_on_password_reset', 'admin.password_reset'),
  ('staff_set_role', 'admin.role_change'),
  ('assign_platform_role', 'admin.role_change'),
  ('revoke_platform_role', 'admin.role_change')
on conflict (proname) do nothing;

create or replace function public._canon_feature_enabled(p_flag text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (select enabled from public.security_feature_flags where flag_key = p_flag),
    true
  );
$$;

create or replace function public._canon_require_feature(p_flag text)
returns void
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public._canon_feature_enabled(p_flag) then
    raise exception 'FEATURE_TEMPORARILY_DISABLED';
  end if;
end;
$$;

create or replace function public._canon_consume_rate_limit(
  p_operation_key text,
  p_units bigint default 1
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  pol public.security_rate_limit_policies%rowtype;
  uid uuid := auth.uid();
  v_scope text;
  v_window timestamptz;
  v_used bigint;
  v_fail_closed boolean := false;
  v_event text;
begin
  select * into pol
    from public.security_rate_limit_policies
   where operation_key = p_operation_key;
  if not found then
    return;
  end if;
  v_fail_closed := pol.fail_closed;
  if not pol.enabled then
    return;
  end if;
  if uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;

  v_scope := uid::text;
  v_window := to_timestamp(
    floor(extract(epoch from timezone('utc', now())) / pol.window_seconds) * pol.window_seconds
  );

  insert into public.security_usage_windows as w (
    operation_key, scope_kind, scope_id, window_started_at, used_count, used_units
  ) values (
    p_operation_key, pol.scope_kind, v_scope, v_window, 1, greatest(coalesce(p_units, 1), 1)
  )
  on conflict (operation_key, scope_kind, scope_id, window_started_at)
  do update set
    used_count = w.used_count + 1,
    used_units = w.used_units + greatest(coalesce(p_units, 1), 1)
  returning used_units into v_used;

  if v_used > pol.limit_count then
    v_event := case
      when pol.exceed_code = 'QUOTA_EXCEEDED' then 'UPLOAD_QUOTA_EXCEEDED'
      when p_operation_key = 'signed_url.request' then 'SIGNED_URL_THROTTLED'
      when p_operation_key like 'vitacora.import.%' then 'IMPORT_THROTTLED'
      else 'RATE_LIMIT_EXCEEDED'
    end;
    begin
      insert into public.security_audit_events (actor_user_id, action, entity_table, entity_id, metadata)
      values (
        case when exists (select 1 from public.persons p where p.user_id = uid) then uid else null end,
        v_event,
        'security_usage_windows',
        null,
        jsonb_build_object('operation_key', p_operation_key, 'used_units', v_used)
      );
    exception when others then
      null;
    end;
    raise exception '%', pol.exceed_code;
  end if;
exception
  when raise_exception then
    raise;
  when others then
    if v_fail_closed then
      raise exception 'RATE_LIMITED';
    end if;
end;
$$;

revoke all on function public._canon_feature_enabled(text) from public, anon, authenticated;
revoke all on function public._canon_require_feature(text) from public, anon, authenticated;
revoke all on function public._canon_consume_rate_limit(text, bigint) from public, anon, authenticated;

create or replace function public.security_consume_rate_limit(
  p_operation_key text,
  p_units bigint default 1
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_operation_key is null
     or not exists (
       select 1 from public.security_rate_limit_policies where operation_key = p_operation_key
     ) then
    raise exception 'VALIDATION';
  end if;
  perform public._canon_consume_rate_limit(p_operation_key, p_units);
  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.security_feature_enabled(p_flag text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public._canon_feature_enabled(p_flag);
$$;

revoke all on function public.security_consume_rate_limit(text, bigint) from public, anon;
revoke all on function public.security_feature_enabled(text) from public, anon;
grant execute on function public.security_consume_rate_limit(text, bigint) to authenticated;
grant execute on function public.security_feature_enabled(text) to authenticated;

-- ---------------------------------------------------------------------------
-- Media register: bucket/MIME/size + quotas. Client MIME is not trusted as sole check.
-- Storage file_size_limit still applies to the actual object.
-- ---------------------------------------------------------------------------
create or replace function public.canon_register_media(
  p_bucket text, p_path text, p_mime text, p_size bigint
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_bucket text := btrim(coalesce(p_bucket, ''));
  v_path text := btrim(coalesce(p_path, ''));
  v_mime text := lower(btrim(coalesce(p_mime, '')));
  v_size bigint := coalesce(p_size, 0);
  v_max bigint;
  v_video boolean := false;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if v_bucket not in ('public-media', 'private-media', 'documents', 'vitacora-import') then
    raise exception 'VALIDATION';
  end if;
  if v_path = '' or v_path like '%..%' or v_path like '/%' then
    raise exception 'VALIDATION';
  end if;
  v_max := case when v_bucket = 'vitacora-import' then 5242880 else 52428800 end;
  if v_size < 1 or v_size > v_max then
    raise exception 'QUOTA_EXCEEDED';
  end if;
  v_video := v_mime like 'video/%';
  if v_video then
    perform public._canon_require_feature('media.video.upload.enabled');
    if v_mime not in ('video/mp4', 'video/quicktime', 'video/webm', 'video/3gpp') then
      raise exception 'VALIDATION';
    end if;
  elsif v_mime like 'image/%' then
    if v_mime not in ('image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif', 'image/gif') then
      raise exception 'VALIDATION';
    end if;
  elsif v_bucket in ('documents', 'vitacora-import') then
    if v_mime not in (
      'application/pdf',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.ms-excel',
      'text/csv',
      'application/octet-stream'
    ) then
      raise exception 'VALIDATION';
    end if;
  else
    raise exception 'VALIDATION';
  end if;

  perform public._canon_consume_rate_limit('media.register', 1);
  perform public._canon_consume_rate_limit('media.upload.bytes.daily', v_size);
  if v_video then
    perform public._canon_consume_rate_limit('media.video.count.daily', 1);
  end if;

  insert into public.media_assets (
    bucket, object_path, mime_type, byte_size, owner_kind, owner_person_id, created_by, lifecycle_status
  ) values (
    v_bucket, v_path, v_mime, v_size, 'PERSON', auth.uid(), auth.uid(), 'READY'
  ) returning id into v_id;
  return v_id;
end;
$$;

revoke all on function public.canon_register_media(text, text, text, bigint) from public, anon;
grant execute on function public.canon_register_media(text, text, text, bigint) to authenticated;

create or replace function public.canon_authorize_media_signed_url(p_asset_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  a public.media_assets%rowtype;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if p_asset_id is null then
    raise exception 'VALIDATION';
  end if;
  select * into a from public.media_assets where id = p_asset_id;
  if not found then
    raise exception 'NOT_FOUND';
  end if;
  if not public._acl_media_readable(auth.uid(), a) then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_consume_rate_limit('signed_url.request', 1);
  return jsonb_build_object(
    'ok', true,
    'bucket', a.bucket,
    'path', a.object_path,
    'ttl_seconds', 600
  );
end;
$$;

revoke all on function public.canon_authorize_media_signed_url(uuid) from public, anon;
grant execute on function public.canon_authorize_media_signed_url(uuid) to authenticated;

-- Social create: same contract + rate/kill switch.
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
declare
  v_id uuid;
  v_kind text := upper(btrim(coalesce(p_content_kind, 'POST')));
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
  if v_kind = 'REEL' then
    perform public._canon_require_feature('reels.create.enabled');
    perform public._canon_consume_rate_limit('social.reel.create', 1);
  else
    perform public._canon_consume_rate_limit('social.post.create', 1);
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

revoke all on function public.canon_create_social_post(text, text, text, uuid, uuid, text, jsonb)
  from public, anon;
grant execute on function public.canon_create_social_post(text, text, text, uuid, uuid, text, jsonb)
  to authenticated;

-- Feed and message list RPCs stay as approved (1053 / 1035).
-- Unbounded jsonb lists are a P1 pagination residual; a hard cap here would hide history.

-- Wrap bound RPCs. Admin keys also re-check AAL2 before consume.
do $$
declare
  r record;
  impl_name text;
  call_args text;
  create_sql text;
  ret_clause text;
  ident text;
  args text;
  consume text;
begin
  for r in
    select p.oid,
           p.proname,
           p.pronargs,
           p.proargnames,
           p.proargmodes,
           b.operation_key
      from public.security_rate_limit_rpc_bindings b
      join pg_proc p on p.proname = b.proname
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.proname not like '%\_sec03\_impl' escape '\'
  loop
    ident := pg_get_function_identity_arguments(r.oid);
    args := pg_get_function_arguments(r.oid);
    ret_clause := pg_get_function_result(r.oid);
    impl_name := r.proname || '_sec03_impl';
    if exists (
      select 1 from pg_proc p2
      join pg_namespace n2 on n2.oid = p2.pronamespace
      where n2.nspname = 'public'
        and p2.proname = impl_name
        and pg_get_function_identity_arguments(p2.oid) = ident
    ) then
      continue;
    end if;

    if r.pronargs = 0 then
      call_args := '';
    elsif r.proargnames is not null then
      select string_agg(quote_ident(r.proargnames[i]), ', ' order by i)
        into call_args
        from generate_series(1, r.pronargs) as i
       where r.proargmodes is null or r.proargmodes[i] in ('i', 'b', 'v');
      call_args := coalesce(call_args, '');
    else
      select string_agg('$' || i::text, ', ') into call_args
        from generate_series(1, r.pronargs) i;
    end if;

    execute format('alter function public.%I(%s) rename to %I', r.proname, ident, impl_name);

    consume := format(
      $c$
          if %L like 'admin.%%' then
            perform public._canon_require_admin_aal2();
          end if;
          if %L like 'vitacora.import.%%' then
            perform public._canon_require_feature('imports.enabled');
          end if;
          perform public._canon_consume_rate_limit(%L, 1);
      $c$, r.operation_key, r.operation_key, r.operation_key
    );

    if ret_clause = 'void' then
      create_sql := format(
        $f$
        create function public.%I(%s)
        returns void
        language plpgsql
        security definer
        set search_path = public
        as $body$
        begin
          %s
          perform public.%I(%s);
        end;
        $body$;
        $f$, r.proname, args, consume, impl_name, call_args
      );
    elsif ret_clause ilike 'setof%' or ret_clause ilike 'table%' then
      create_sql := format(
        $f$
        create function public.%I(%s)
        returns %s
        language plpgsql
        security definer
        set search_path = public
        as $body$
        begin
          %s
          return query select * from public.%I(%s);
        end;
        $body$;
        $f$, r.proname, args, ret_clause, consume, impl_name, call_args
      );
    else
      create_sql := format(
        $f$
        create function public.%I(%s)
        returns %s
        language plpgsql
        security definer
        set search_path = public
        as $body$
        begin
          %s
          return public.%I(%s);
        end;
        $body$;
        $f$, r.proname, args, ret_clause, consume, impl_name, call_args
      );
    end if;
    execute create_sql;
    execute format('revoke all on function public.%I(%s) from public, anon, authenticated', impl_name, ident);
    execute format('revoke all on function public.%I(%s) from public, anon', r.proname, ident);
    execute format('grant execute on function public.%I(%s) to authenticated', r.proname, ident);
  end loop;
end $$;

-- Direct table writes must not bypass RPC rate limits.
revoke insert, update, delete on table
  public.social_posts,
  public.social_stories,
  public.social_comments,
  public.social_reactions,
  public.friendships,
  public.messages,
  public.conversations,
  public.conversation_participants,
  public.content_reports,
  public.lost_found_alerts,
  public.media_assets
from anon, authenticated;

-- Aggregates for a future cost view. No grant to clients.
create or replace view public.security_usage_window_totals as
select
  operation_key,
  scope_kind,
  window_started_at,
  count(*)::bigint as scope_rows,
  sum(used_count)::bigint as used_count,
  sum(used_units)::bigint as used_units
from public.security_usage_windows
group by operation_key, scope_kind, window_started_at;

revoke all on public.security_usage_window_totals from public, anon, authenticated;

notify pgrst, 'reload schema';
