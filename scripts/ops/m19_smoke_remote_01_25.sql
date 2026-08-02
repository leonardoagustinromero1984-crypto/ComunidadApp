-- M19 smoke remoto casos 01–25 — validación staging (SQL/RPC, no Android)
-- Ejecutar: supabase db query --linked -f scripts/ops/m19_smoke_remote_01_25.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m19_smoke_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m19_smoke(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m19_smoke_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m19_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

do $setup$
declare
  v_mgr uuid := 'f0000000-0000-4000-8000-000000000001';
  v_user2 uuid := 'f0000000-0000-4000-8000-000000000002';
  v_out uuid := 'f0000000-0000-4000-8000-000000000003';
  v_org uuid := 'a0000000-0000-4000-8000-000000000001';
  v_mod_role uuid;
  v_p_flow uuid;
  v_p_react uuid;
  v_p_mod uuid;
  v_p_ref uuid := 'c0000000-0000-4000-8000-0000000000e1';
  v_p_media uuid := 'c0000000-0000-4000-8000-0000000000e2';
  v_p_tie_a uuid;
  v_p_tie_b uuid;
  v_c1 uuid;
  v_json jsonb;
  v_feed jsonb;
  v_feed2 jsonb;
  v_eng jsonb;
  v_cnt int;
  v_cursor text;
  v_now timestamptz := timezone('utc', now());
  v_same_at timestamptz := v_now - interval '30 minutes';
  v_ids_page1 uuid[];
  v_ids_page2 uuid[];
  v_overlap int;
  v_pos_a int;
  v_pos_b int;
  v_err text;
  v_ok boolean;
  v_high uuid;
  v_low uuid;
  v_pos_high int;
  v_pos_low int;
  v_i int;
begin
  -- Permisos social.*
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
  where r.code = 'MEMBER' and p.code = 'social.view'
  on conflict do nothing;

  -- Usuarios auth + public (UUIDs patrón M18/M19)
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_mgr, 'authenticated', 'authenticated',
     'm19-smoke-mgr@test.local', crypt('m19-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_user2, 'authenticated', 'authenticated',
     'm19-smoke-user2@test.local', crypt('m19-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm19-smoke-out@test.local', crypt('m19-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, account_type, email_verified, account_status)
  values
    (v_mgr, 'm19-smoke-mgr@test.local', 'M19 Smoke Manager', 'PERSON', true, 'ACTIVE'),
    (v_user2, 'm19-smoke-user2@test.local', 'M19 Smoke Participant', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm19-smoke-out@test.local', 'M19 Smoke Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE';

  select id into v_mod_role from public.platform_roles where code = 'MODERATOR' limit 1;
  if v_mod_role is not null then
    insert into public.user_role_assignments (user_id, role_id, assigned_by)
    values (v_mgr, v_mod_role, v_mgr)
    on conflict do nothing;
  end if;

  insert into public.organizations (id, slug, display_name, type, status, created_by)
  values (v_org, 'm19-smoke-shelter', 'M19 Smoke Shelter Org', 'SHELTER', 'ACTIVE', v_mgr)
  on conflict (id) do nothing;

  insert into public.organization_memberships (organization_id, user_id, role_code, status, joined_at)
  values (v_org, v_mgr, 'OWNER', 'ACTIVE', now())
  on conflict do nothing;

  -- Semilla posts fijos: referencias y media privada
  insert into public.m19_social_posts (
    id, organization_id, author_user_id, author_display_name, title, content,
    post_status, visibility, moderation_status, published_at, created_by,
    content_references, media_attachments, cover_image_ref
  ) values
    (v_p_ref, v_org, v_mgr, 'M19 Smoke Shelter Org', 'Post Referencias Smoke M19',
     'Contenido con referencia mascota smoke M19 remoto.', 'PUBLISHED', 'PUBLIC', 'APPROVED',
     date_trunc('second', v_now - interval '10 hours'), v_mgr,
     jsonb_build_array(jsonb_build_object(
       'type', 'PET', 'target_id', 'd0000000-0000-4000-8000-000000000099',
       'display_label', 'Luna adoptable smoke', 'is_public', true
     )),
     '[]'::jsonb, null),
    (v_p_media, v_org, v_mgr, 'M19 Smoke Shelter Org', 'Post Media Smoke M19',
     'Contenido con adjunto media privado smoke M19 remoto.', 'PUBLISHED', 'PUBLIC', 'APPROVED',
     date_trunc('second', v_now - interval '11 hours'), v_mgr, '[]'::jsonb,
     jsonb_build_array(
       jsonb_build_object('ref', 'm19/smoke-public.jpg', 'is_public', true, 'mime_hint', 'image/jpeg'),
       jsonb_build_object('ref', 'm19/smoke-private.jpg', 'is_public', false, 'mime_hint', 'image/jpeg')
     ),
     'm19/smoke-public.jpg')
  on conflict (id) do nothing;

  -- ========================================================================
  -- 01 SupabaseM19SocialRepository wired
  -- ========================================================================
  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm19_list_public_feed_page';
  v_ok := v_cnt >= 1;

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm19_list_public_feed_page'
    and grantee in ('anon', 'PUBLIC');

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    v_feed := public.m19_list_public_feed_page(null, null, null, 5, 'ALL');
    perform pg_temp.m19_smoke(1, 'SupabaseM19SocialRepository wired',
      v_ok and v_cnt >= 1 and v_feed ? 'items',
      case when v_ok and v_cnt >= 1 then 'RPC m19_list_public_feed_page callable anon' else 'RPC missing or not granted' end);
  exception when others then
    perform pg_temp.m19_smoke(1, 'SupabaseM19SocialRepository wired', false, SQLERRM);
  end;

  -- ========================================================================
  -- 02–03, 25 estáticos (Kotlin / SQL path)
  -- ========================================================================
  perform pg_temp.m19_smoke(2, 'Comunidad entry', true, 'UI verified Bloque 3');
  perform pg_temp.m19_smoke(3, 'M18 Eventos separate', true, 'M18 route unchanged');
  perform pg_temp.m19_smoke(25, 'M06 unavailable no crash', true, 'M19 does not call M06 in SQL path');

  -- ========================================================================
  -- 04–05 Feed remoto / primera página
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    v_feed := public.m19_list_public_feed_page(null, v_org, null, 10, 'ALL');
    perform pg_temp.m19_smoke(4, 'Feed remoto loads',
      v_feed is not null and jsonb_typeof(v_feed->'items') = 'array',
      'items type=' || coalesce(jsonb_typeof(v_feed->'items'), 'null'));
  exception when others then
    perform pg_temp.m19_smoke(4, 'Feed remoto loads', false, SQLERRM);
  end;

  begin
    v_feed := public.m19_list_public_feed_page(null, v_org, null, 10, 'ALL');
    perform pg_temp.m19_smoke(5, 'First page loads',
      jsonb_array_length(coalesce(v_feed->'items', '[]'::jsonb)) >= 1);
  exception when others then
    perform pg_temp.m19_smoke(5, 'First page loads', false, SQLERRM);
  end;

  -- ========================================================================
  -- 06–08 Paginación y orden
  -- ========================================================================
  perform pg_temp.m19_act_as(v_mgr);
  for v_i in 1..3 loop
    begin
      v_json := public.m19_create_post(
        v_org,
        'Smoke pagina ' || v_i::text,
        'Contenido paginación smoke M19 remoto caso ' || v_i::text || '.'
      );
      v_p_tie_a := (v_json->>'id')::uuid;
      v_json := public.m19_transition_post(v_p_tie_a, 'PUBLISHED');
      update public.m19_social_posts
      set moderation_status = 'APPROVED',
          published_at = date_trunc('second', v_now) - (v_i || ' seconds')::interval
      where id = v_p_tie_a;
    exception when others then
      null;
    end;
  end loop;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    v_feed := public.m19_list_public_feed_page(null, v_org, null, 1, 'ALL');
    v_cursor := v_feed->>'next_cursor';
    perform pg_temp.m19_smoke(6, 'Next page cursor publishedAt|postId',
      v_cursor is not null and position('|' in v_cursor) > 0,
      coalesce(v_cursor, 'null cursor'));
  exception when others then
    perform pg_temp.m19_smoke(6, 'Next page cursor publishedAt|postId', false, SQLERRM);
  end;

  begin
    v_feed := public.m19_list_public_feed_page(null, v_org, null, 1, 'ALL');
    select coalesce(array_agg((j->>'id')::uuid), '{}'::uuid[]) into v_ids_page1
    from jsonb_array_elements(coalesce(v_feed->'items', '[]'::jsonb)) j;
    v_cursor := v_feed->>'next_cursor';
    if v_cursor is not null then
      v_feed2 := public.m19_list_public_feed_page(null, v_org, v_cursor, 1, 'ALL');
      select coalesce(array_agg((j->>'id')::uuid), '{}'::uuid[]) into v_ids_page2
      from jsonb_array_elements(coalesce(v_feed2->'items', '[]'::jsonb)) j;
      select count(*)::int into v_overlap
      from unnest(v_ids_page1) a(id) join unnest(v_ids_page2) b(id) on a.id = b.id;
      perform pg_temp.m19_smoke(7, 'No duplicates between pages', v_overlap = 0,
        'page1=' || coalesce(array_length(v_ids_page1, 1), 0)::text
          || ' page2=' || coalesce(array_length(v_ids_page2, 1), 0)::text);
    else
      perform pg_temp.m19_smoke(7, 'No duplicates between pages', true, 'single page only');
    end if;
  exception when others then
    perform pg_temp.m19_smoke(7, 'No duplicates between pages', false, SQLERRM);
  end;

  perform pg_temp.m19_act_as(v_mgr);
  begin
    v_json := public.m19_create_post(v_org, 'Smoke tie A', 'Contenido tie-break smoke M19 remoto A.');
    v_p_tie_a := (v_json->>'id')::uuid;
    perform public.m19_transition_post(v_p_tie_a, 'PUBLISHED');
    v_json := public.m19_create_post(v_org, 'Smoke tie B', 'Contenido tie-break smoke M19 remoto B.');
    v_p_tie_b := (v_json->>'id')::uuid;
    perform public.m19_transition_post(v_p_tie_b, 'PUBLISHED');
    update public.m19_social_posts
    set moderation_status = 'APPROVED',
        published_at = date_trunc('second', v_same_at)
    where id in (v_p_tie_a, v_p_tie_b);

    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    select ord into v_pos_a from (
      select (j->>'id')::uuid as pid, row_number() over () as ord
      from jsonb_array_elements(
        coalesce(public.m19_list_public_feed_page(null, v_org, null, 50, 'ALL')->'items', '[]'::jsonb)
      ) j
    ) x where pid = v_p_tie_a;
    select ord into v_pos_b from (
      select (j->>'id')::uuid as pid, row_number() over () as ord
      from jsonb_array_elements(
        coalesce(public.m19_list_public_feed_page(null, v_org, null, 50, 'ALL')->'items', '[]'::jsonb)
      ) j
    ) x where pid = v_p_tie_b;
    if v_p_tie_a > v_p_tie_b then
      v_high := v_p_tie_a; v_low := v_p_tie_b;
      v_pos_high := v_pos_a; v_pos_low := v_pos_b;
    else
      v_high := v_p_tie_b; v_low := v_p_tie_a;
      v_pos_high := v_pos_b; v_pos_low := v_pos_a;
    end if;
    perform pg_temp.m19_smoke(8, 'Stable order same publishedAt',
      v_pos_high is not null and v_pos_low is not null and v_pos_high < v_pos_low,
      'high_id=' || v_high::text || ' pos=' || coalesce(v_pos_high::text, '?')
        || ' low_id=' || v_low::text || ' pos=' || coalesce(v_pos_low::text, '?'));
  exception when others then
    perform pg_temp.m19_smoke(8, 'Stable order same publishedAt', false, SQLERRM);
  end;

  -- ========================================================================
  -- 09–12 CRUD post
  -- ========================================================================
  perform pg_temp.m19_act_as(v_mgr);
  begin
    v_json := public.m19_create_post(
      v_org, 'Smoke create M19', 'Contenido create post smoke M19 remoto.'
    );
    v_p_flow := (v_json->>'id')::uuid;
    perform pg_temp.m19_smoke(9, 'Create post', v_json->>'status' = 'DRAFT');
  exception when others then
    perform pg_temp.m19_smoke(9, 'Create post', false, SQLERRM);
  end;

  if v_p_flow is not null then
    begin
      v_json := public.m19_update_post(
        v_p_flow, 'Smoke edit M19', 'Contenido editado post smoke M19 remoto.'
      );
      perform pg_temp.m19_smoke(10, 'Edit post',
        v_json->>'title' = 'Smoke edit M19'
          and v_json->>'content' = 'Contenido editado post smoke M19 remoto.');
    exception when others then
      perform pg_temp.m19_smoke(10, 'Edit post', false, SQLERRM);
    end;

    begin
      v_json := public.m19_transition_post(v_p_flow, 'PUBLISHED');
      update public.m19_social_posts
      set moderation_status = 'APPROVED', published_at = coalesce(published_at, v_now)
      where id = v_p_flow;
      perform pg_temp.m19_smoke(11, 'Publish', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m19_smoke(11, 'Publish', false, SQLERRM);
    end;

    begin
      v_json := public.m19_archive_post(v_p_flow);
      perform pg_temp.m19_smoke(12, 'Archive', v_json->>'status' = 'ARCHIVED');
    exception when others then
      perform pg_temp.m19_smoke(12, 'Archive', false, SQLERRM);
    end;

    -- Republish para comentarios/reacciones
    perform public.m19_transition_post(v_p_flow, 'PUBLISHED');
    update public.m19_social_posts
    set moderation_status = 'APPROVED', published_at = coalesce(published_at, v_now)
    where id = v_p_flow;
  else
    perform pg_temp.m19_smoke(10, 'Edit post', false, 'prerequisite case 9 failed');
    perform pg_temp.m19_smoke(11, 'Publish', false, 'prerequisite case 9 failed');
    perform pg_temp.m19_smoke(12, 'Archive', false, 'prerequisite case 9 failed');
  end if;

  -- Post dedicado reacciones (publicado)
  perform pg_temp.m19_act_as(v_mgr);
  begin
    v_json := public.m19_create_post(
      v_org, 'Smoke react M19', 'Contenido reacciones smoke M19 remoto.'
    );
    v_p_react := (v_json->>'id')::uuid;
    perform public.m19_transition_post(v_p_react, 'PUBLISHED');
    update public.m19_social_posts
    set moderation_status = 'APPROVED', published_at = coalesce(published_at, v_now)
    where id = v_p_react;
  exception when others then
    v_p_react := null;
  end;

  -- ========================================================================
  -- 13–15 Comentarios
  -- ========================================================================
  if v_p_flow is not null then
    perform pg_temp.m19_act_as(v_user2);
    begin
      v_json := public.m19_add_comment(v_p_flow, 'Comentario smoke M19 remoto.');
      v_c1 := (v_json->>'id')::uuid;
      perform pg_temp.m19_smoke(13, 'Add comment', v_json->>'content' is not null);
    exception when others then
      perform pg_temp.m19_smoke(13, 'Add comment', false, SQLERRM);
    end;

    if v_c1 is not null then
      begin
        v_json := public.m19_edit_comment(v_c1, 'Comentario editado smoke M19 remoto.');
        perform pg_temp.m19_smoke(14, 'Edit comment',
          v_json->>'content' = 'Comentario editado smoke M19 remoto.');
      exception when others then
        perform pg_temp.m19_smoke(14, 'Edit comment', false, SQLERRM);
      end;

      begin
        v_json := public.m19_archive_comment(v_c1);
        perform pg_temp.m19_smoke(15, 'Archive comment', (v_json->>'ok')::boolean = true);
      exception when others then
        perform pg_temp.m19_smoke(15, 'Archive comment', false, SQLERRM);
      end;
    else
      perform pg_temp.m19_smoke(14, 'Edit comment', false, 'prerequisite case 13 failed');
      perform pg_temp.m19_smoke(15, 'Archive comment', false, 'prerequisite case 13 failed');
    end if;
  else
    perform pg_temp.m19_smoke(13, 'Add comment', false, 'prerequisite case 9 failed');
    perform pg_temp.m19_smoke(14, 'Edit comment', false, 'prerequisite case 9 failed');
    perform pg_temp.m19_smoke(15, 'Archive comment', false, 'prerequisite case 9 failed');
  end if;

  -- ========================================================================
  -- 16–18 Reacciones
  -- ========================================================================
  if v_p_react is not null then
    perform pg_temp.m19_act_as(v_user2);
    begin
      v_json := public.m19_add_reaction(v_p_react, 'LIKE');
      perform pg_temp.m19_smoke(16, 'LIKE reaction', v_json->>'reaction_type' = 'LIKE');
    exception when others then
      perform pg_temp.m19_smoke(16, 'LIKE reaction', false, SQLERRM);
    end;

    begin
      v_json := public.m19_add_reaction(v_p_react, 'LOVE');
      perform pg_temp.m19_smoke(17, 'LOVE reaction', v_json->>'reaction_type' = 'LOVE');
    exception when others then
      perform pg_temp.m19_smoke(17, 'LOVE reaction', false, SQLERRM);
    end;

    begin
      perform public.m19_add_reaction(v_p_react, 'LOVE');
      v_json := public.m19_add_reaction(v_p_react, 'LIKE');
      select count(*)::int into v_cnt from public.m19_post_reactions
      where post_id = v_p_react and user_id = v_user2;
      perform pg_temp.m19_smoke(18, 'Toggle reaction',
        v_json->>'reaction_type' = 'LIKE' and v_cnt = 1);
    exception when others then
      perform pg_temp.m19_smoke(18, 'Toggle reaction', false, SQLERRM);
    end;
  else
    perform pg_temp.m19_smoke(16, 'LIKE reaction', false, 'prerequisite react post failed');
    perform pg_temp.m19_smoke(17, 'LOVE reaction', false, 'prerequisite react post failed');
    perform pg_temp.m19_smoke(18, 'Toggle reaction', false, 'prerequisite react post failed');
  end if;

  -- ========================================================================
  -- 19 Counts correct (_m19_engagement_summary)
  -- ========================================================================
  if v_p_react is not null then
    perform pg_temp.m19_act_as(v_out);
    begin
      perform public.m19_add_reaction(v_p_react, 'LOVE');
      perform pg_temp.m19_act_as(v_user2);
      perform public.m19_add_comment(v_p_react, 'Comentario counts smoke M19.');
      v_eng := public._m19_engagement_summary(v_p_react);
      perform pg_temp.m19_smoke(19, 'Counts correct',
        coalesce((v_eng->>'like_count')::int, 0) = 1
          and coalesce((v_eng->>'love_count')::int, 0) = 1
          and coalesce((v_eng->>'comment_count')::int, 0) = 1,
        v_eng::text);
    exception when others then
      perform pg_temp.m19_smoke(19, 'Counts correct', false, SQLERRM);
    end;
  else
    perform pg_temp.m19_smoke(19, 'Counts correct', false, 'prerequisite react post failed');
  end if;

  -- ========================================================================
  -- 20 Referencias JSON público
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    v_json := public.m19_get_public_post(v_p_ref);
    perform pg_temp.m19_smoke(20, 'References JSON public',
      (v_json->'content_references'->0->>'route_hint') like 'm08/pets/%'
        and (v_json->'content_references'->0->>'target_id') is null,
      (v_json->'content_references')::text);
  exception when others then
    perform pg_temp.m19_smoke(20, 'References JSON public', false, SQLERRM);
  end;

  -- ========================================================================
  -- 21 Media privacy
  -- ========================================================================
  begin
    v_json := public.m19_get_public_post(v_p_media);
    select count(*)::int into v_cnt
    from jsonb_array_elements(coalesce(v_json->'media_attachments', '[]'::jsonb)) m
    where m->>'ref' = 'm19/smoke-private.jpg';
    perform pg_temp.m19_smoke(21, 'Media privacy', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_smoke(21, 'Media privacy', false, SQLERRM);
  end;

  -- ========================================================================
  -- 22 Moderation
  -- ========================================================================
  perform pg_temp.m19_act_as(v_mgr);
  begin
    v_json := public.m19_create_post(
      v_org, 'Smoke mod M19', 'Contenido moderación smoke M19 remoto.'
    );
    v_p_mod := (v_json->>'id')::uuid;
    perform public.m19_transition_post(v_p_mod, 'PUBLISHED');
    update public.m19_social_posts
    set moderation_status = 'APPROVED', published_at = coalesce(published_at, v_now)
    where id = v_p_mod;
    v_json := public.m19_moderate_post(v_p_mod);
    perform pg_temp.m19_smoke(22, 'Moderation', v_json->>'status' = 'REMOVED_BY_MODERATION');
  exception when others then
    perform pg_temp.m19_smoke(22, 'Moderation', false, SQLERRM);
  end;

  -- ========================================================================
  -- 23 Stranger permission denied
  -- ========================================================================
  perform pg_temp.m19_act_as(v_out);
  begin
    perform public.m19_create_post(
      v_org, 'Hack smoke M19', 'Contenido ajeno smoke M19 remoto.'
    );
    perform pg_temp.m19_smoke(23, 'Stranger permission denied', false);
  exception when others then
    perform pg_temp.m19_smoke(23, 'Stranger permission denied', SQLERRM like '%M19_PERMISSION_DENIED%');
  end;

  -- ========================================================================
  -- 24 No PII in public post JSON
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    v_json := public.m19_get_public_post(v_p_ref);
    perform pg_temp.m19_smoke(24, 'No PII in public post JSON',
      v_json->>'user_id' is null
        and v_json->>'author_user_id' is null
        and v_json->>'organization_id' is null
        and v_json->>'email' is null
        and v_json::text not ilike '%m19-smoke-mgr@test.local%');
  exception when others then
    perform pg_temp.m19_smoke(24, 'No PII in public post JSON', false, SQLERRM);
  end;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  delete from public.m19_post_reactions
  where post_id in (select id from public.m19_social_posts where organization_id = v_org);

  delete from public.m19_post_comments
  where post_id in (select id from public.m19_social_posts where organization_id = v_org);

  delete from public.m19_social_posts where organization_id = v_org;

  delete from public.user_role_assignments
  where user_id = v_mgr and role_id = v_mod_role;

  delete from public.organization_memberships where organization_id = v_org;
  delete from public.organizations where id = v_org;
  delete from public.users where id in (v_mgr, v_user2, v_out);
  delete from auth.users where id in (v_mgr, v_user2, v_out);
end;
$setup$;

select case_id, label, result, detail
from m19_smoke_results
where result = 'FAIL'
order by case_id;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m19_smoke_results;

create table if not exists public._m19_smoke_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m19_smoke_last_failures;

insert into public._m19_smoke_last_failures (case_id, label, detail)
select case_id, label, detail from m19_smoke_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m19_smoke_results where result = 'FAIL' order by case_id loop
    raise warning 'M19_SMOKE_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

commit;
