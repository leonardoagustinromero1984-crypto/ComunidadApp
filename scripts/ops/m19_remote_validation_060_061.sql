-- M19 migraciones 060+061 — validación remota staging (casos 01–105)
-- Ejecutar: supabase db query --linked -f scripts/ops/m19_remote_validation_060_061.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m19_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m19_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m19_val_results (case_id, label, result, detail)
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
  v_bad_org uuid := 'a0000000-0000-4000-8000-000000000002';
  v_p_draft uuid := 'c0000000-0000-4000-8000-000000000001';
  v_p_pub uuid := 'c0000000-0000-4000-8000-000000000002';
  v_p_hidden uuid := 'c0000000-0000-4000-8000-000000000003';
  v_p_blocked uuid := 'c0000000-0000-4000-8000-000000000004';
  v_p_pending uuid := 'c0000000-0000-4000-8000-000000000005';
  v_p_orgvis uuid := 'c0000000-0000-4000-8000-000000000006';
  v_p_pet uuid := 'c0000000-0000-4000-8000-000000000007';
  v_p_media uuid := 'c0000000-0000-4000-8000-000000000008';
  v_p_text uuid := 'c0000000-0000-4000-8000-000000000009';
  v_p_arch uuid := 'c0000000-0000-4000-8000-00000000000a';
  v_p_flow uuid;
  v_p_bad_trans uuid;
  v_c_user2 uuid;
  v_c_arch uuid;
  v_json jsonb;
  v_feed jsonb;
  v_cnt int;
  v_key text;
  v_ok boolean;
  v_cursor text;
  v_now timestamptz := timezone('utc', now());
  v_err text;
begin
  -- Permisos social.* (060)
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

  -- Usuarios auth + public (reutiliza UUIDs patrón M18)
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_mgr, 'authenticated', 'authenticated',
     'm19-mgr@test.local', crypt('m19-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_user2, 'authenticated', 'authenticated',
     'm19-user2@test.local', crypt('m19-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm19-out@test.local', crypt('m19-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, account_type, email_verified, account_status)
  values
    (v_mgr, 'm19-mgr@test.local', 'M19 Manager', 'PERSON', true, 'ACTIVE'),
    (v_user2, 'm19-user2@test.local', 'M19 Participant', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm19-out@test.local', 'M19 Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE';

  insert into public.organizations (id, slug, display_name, type, status, created_by)
  values
    (v_org, 'm19-val-shelter', 'M19 Val Shelter Org', 'SHELTER', 'ACTIVE', v_mgr),
    (v_bad_org, 'm19-val-petshop', 'M19 Val Pet Shop Org', 'PET_SHOP', 'ACTIVE', v_out)
  on conflict (id) do nothing;

  insert into public.organization_memberships (organization_id, user_id, role_code, status, joined_at)
  values (v_org, v_mgr, 'OWNER', 'ACTIVE', now())
  on conflict do nothing;

  -- Semilla posts fijos para pruebas estructurales / RLS / privacidad
  insert into public.m19_social_posts (
    id, organization_id, author_user_id, author_display_name, title, content,
    post_status, visibility, moderation_status, published_at, created_by,
    content_references, media_attachments, cover_image_ref
  ) values
    (v_p_draft, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Borrador M19',
     'Contenido borrador de prueba M19 validación remota.', 'DRAFT', 'PUBLIC', null, null, v_mgr,
     '[]'::jsonb, '[]'::jsonb, null),
    (v_p_pub, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Publicado M19',
     'Contenido publicado de prueba M19 validación remota feed.', 'PUBLISHED', 'PUBLIC', 'APPROVED',
     v_now - interval '1 hour', v_mgr, '[]'::jsonb, '[]'::jsonb, null),
    (v_p_hidden, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Oculto M19',
     'Contenido oculto de prueba M19 validación remota hidden.', 'HIDDEN', 'PUBLIC', 'APPROVED',
     v_now - interval '2 hours', v_mgr, '[]'::jsonb, '[]'::jsonb, null),
    (v_p_blocked, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Bloqueado M19',
     'Contenido bloqueado de prueba M19 validación remota moderación.', 'PUBLISHED', 'PUBLIC', 'BLOCKED',
     v_now - interval '3 hours', v_mgr, '[]'::jsonb, '[]'::jsonb, null),
    (v_p_pending, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Pendiente M19',
     'Contenido pendiente de prueba M19 validación remota moderación.', 'PUBLISHED', 'PUBLIC', 'PENDING',
     v_now - interval '4 hours', v_mgr, '[]'::jsonb, '[]'::jsonb, null),
    (v_p_orgvis, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Org Only M19',
     'Contenido solo org de prueba M19 validación remota visibility.', 'PUBLISHED', 'ORGANIZATION', 'APPROVED',
     v_now - interval '5 hours', v_mgr, '[]'::jsonb, '[]'::jsonb, null),
    (v_p_pet, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Mascota M19',
     'Contenido con referencia mascota M19 validación remota filtros.', 'PUBLISHED', 'PUBLIC', 'APPROVED',
     v_now - interval '6 hours', v_mgr,
     jsonb_build_array(jsonb_build_object(
       'type', 'PET', 'target_id', 'd0000000-0000-4000-8000-000000000099',
       'display_label', 'Luna adoptable', 'is_public', true
     )),
     '[]'::jsonb, null),
    (v_p_media, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Media M19',
     'Contenido con adjunto media M19 validación remota filtros.', 'PUBLISHED', 'PUBLIC', 'APPROVED',
     v_now - interval '7 hours', v_mgr, '[]'::jsonb,
     jsonb_build_array(
       jsonb_build_object('ref', 'm19/public-cover.jpg', 'is_public', true, 'mime_hint', 'image/jpeg'),
       jsonb_build_object('ref', 'm19/private-internal.jpg', 'is_public', false, 'mime_hint', 'image/jpeg')
     ),
     'm19/public-cover.jpg'),
    (v_p_text, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Texto M19',
     'Contenido solo texto M19 validación remota sin media.', 'PUBLISHED', 'PUBLIC', 'APPROVED',
     v_now - interval '8 hours', v_mgr, '[]'::jsonb, '[]'::jsonb, null),
    (v_p_arch, v_org, v_mgr, 'M19 Val Shelter Org', 'Post Archivado M19',
     'Contenido archivado de prueba M19 validación remota archive.', 'ARCHIVED', 'PUBLIC', 'APPROVED',
     v_now - interval '9 hours', v_mgr, '[]'::jsonb, '[]'::jsonb, null)
  on conflict (id) do nothing;

  -- ========================================================================
  -- ESTRUCTURA 01–25
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name in (
    'm19_social_posts', 'm19_post_comments', 'm19_post_reactions'
  );
  perform pg_temp.m19_val(1, 'Tres tablas M19', v_cnt = 3);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm19_social_posts'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'organization_id';
  perform pg_temp.m19_val(2, 'FK organization_id posts', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm19_social_posts'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'author_user_id';
  perform pg_temp.m19_val(3, 'FK author_user_id posts', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm19_post_comments'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'post_id';
  perform pg_temp.m19_val(4, 'FK post_id comentarios', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm19_post_comments'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'user_id';
  perform pg_temp.m19_val(5, 'FK user_id comentarios', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm19_post_reactions'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'post_id';
  perform pg_temp.m19_val(6, 'FK post_id reacciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm19_post_reactions'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'user_id';
  perform pg_temp.m19_val(7, 'FK user_id reacciones', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm19_post_reactions'
    and indexdef ilike '%post_id%user_id%';
  perform pg_temp.m19_val(8, 'UNIQUE post_id+user_id reacciones', v_cnt >= 1);

  begin
    insert into public.m19_social_posts (
      organization_id, author_user_id, title, content, post_status
    ) values (
      v_org, v_mgr, '', 'Contenido título vacío M19 test.', 'DRAFT'
    );
    perform pg_temp.m19_val(9, 'CHECK title length', false);
  exception when check_violation then
    perform pg_temp.m19_val(9, 'CHECK title length', true);
  end;

  begin
    insert into public.m19_social_posts (
      organization_id, author_user_id, title, content, post_status
    ) values (
      v_org, v_mgr, 'Corto', 'abc', 'DRAFT'
    );
    perform pg_temp.m19_val(10, 'CHECK content length', false);
  exception when check_violation then
    perform pg_temp.m19_val(10, 'CHECK content length', true);
  end;

  begin
    insert into public.m19_post_comments (post_id, user_id, content)
    values (v_p_pub, v_user2, '');
    perform pg_temp.m19_val(11, 'CHECK comment length', false);
  exception when check_violation then
    perform pg_temp.m19_val(11, 'CHECK comment length', true);
  end;

  begin
    insert into public.m19_social_posts (
      organization_id, author_user_id, title, content, post_status
    ) values (
      v_org, v_mgr, 'Estado inválido', 'Contenido estado inválido M19 test.', 'INVALID'
    );
    perform pg_temp.m19_val(12, 'CHECK post_status enum', false);
  exception when check_violation then
    perform pg_temp.m19_val(12, 'CHECK post_status enum', true);
  end;

  begin
    insert into public.m19_post_reactions (post_id, user_id, reaction_type)
    values (v_p_pub, v_out, 'INVALID');
    perform pg_temp.m19_val(13, 'CHECK reaction_type enum', false);
  exception when check_violation then
    perform pg_temp.m19_val(13, 'CHECK reaction_type enum', true);
  end;

  begin
    insert into public.m19_social_posts (
      organization_id, author_user_id, title, content, post_status, visibility
    ) values (
      v_org, v_mgr, 'Vis inválida', 'Contenido visibilidad inválida M19 test.', 'DRAFT', 'FOLLOWERS'
    );
    perform pg_temp.m19_val(14, 'CHECK visibility enum (061)', false);
  exception when check_violation then
    perform pg_temp.m19_val(14, 'CHECK visibility enum (061)', true);
  end;

  begin
    insert into public.m19_social_posts (
      organization_id, author_user_id, title, content, post_status, content_references
    ) values (
      v_org, v_mgr, 'Refs inválidas', 'Contenido refs inválidas M19 test.', 'DRAFT', '{}'::jsonb
    );
    perform pg_temp.m19_val(15, 'CHECK content_references array (061)', false);
  exception when check_violation then
    perform pg_temp.m19_val(15, 'CHECK content_references array (061)', true);
  end;

  begin
    insert into public.m19_social_posts (
      organization_id, author_user_id, title, content, post_status, media_attachments
    ) values (
      v_org, v_mgr, 'Media inválida', 'Contenido media inválida M19 test.', 'DRAFT', '{}'::jsonb
    );
    perform pg_temp.m19_val(16, 'CHECK media_attachments array (061)', false);
  exception when check_violation then
    perform pg_temp.m19_val(16, 'CHECK media_attachments array (061)', true);
  end;

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm19_social_posts'
    and column_name = 'visibility';
  perform pg_temp.m19_val(17, 'Columna visibility (061)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm19_post_comments'
    and column_name = 'archived';
  perform pg_temp.m19_val(18, 'Columna archived comentarios (061)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm19_post_comments'
    and column_name = 'updated_at' and udt_name = 'timestamptz';
  perform pg_temp.m19_val(19, 'Columna updated_at comentarios (061)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm19_social_posts'
    and indexname = 'm19_posts_org_idx';
  perform pg_temp.m19_val(20, 'Índice m19_posts_org_idx', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm19_post_reactions'
    and indexname = 'm19_reaction_post_user_uniq';
  perform pg_temp.m19_val(21, 'Índice m19_reaction_post_user_uniq', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm19_social_posts'
    and indexname = 'm19_posts_feed_cursor_idx';
  perform pg_temp.m19_val(22, 'Índice m19_posts_feed_cursor_idx (061)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm19_social_posts' and c.relrowsecurity;
  perform pg_temp.m19_val(23, 'RLS m19_social_posts', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm19_post_comments' and c.relrowsecurity;
  perform pg_temp.m19_val(24, 'RLS m19_post_comments', v_cnt = 1);

  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname in (
      'm19_create_post', 'm19_transition_post', 'm19_list_public_feed_page',
      'm19_archive_post', 'm19_add_comment', 'm19_edit_comment', 'm19_archive_comment',
      'm19_add_reaction', 'm19_remove_reaction', '_m19_public_post_json'
    );
  perform pg_temp.m19_val(25, 'RPCs M19 clave existen', v_cnt = 10);

  -- ========================================================================
  -- RLS / PERMISOS 26–55
  -- ========================================================================
  select count(*)::int into v_cnt from public.organization_permissions
  where code = 'social.view';
  perform pg_temp.m19_val(26, 'Permiso social.view', v_cnt = 1);

  select count(*)::int into v_cnt from public.organization_permissions
  where code = 'social.manage';
  perform pg_temp.m19_val(27, 'Permiso social.manage', v_cnt = 1);

  select count(*)::int into v_cnt
  from public.organization_role_permissions orp
  join public.organization_roles r on r.id = orp.role_id
  join public.organization_permissions p on p.id = orp.permission_id
  where r.code = 'OWNER' and p.code = 'social.manage';
  perform pg_temp.m19_val(28, 'OWNER tiene social.manage', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm19_social_posts' and grantee = 'anon';
  perform pg_temp.m19_val(29, 'Anon sin grant posts', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm19_post_comments' and grantee = 'anon';
  perform pg_temp.m19_val(30, 'Anon sin grant comentarios', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm19_post_reactions' and grantee = 'anon';
  perform pg_temp.m19_val(31, 'Anon sin grant reacciones', v_cnt = 0);

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m19_social_posts;
    reset role;
    perform pg_temp.m19_val(32, 'Anon sin filas posts (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m19_val(32, 'Anon sin filas posts (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m19_val(32, 'Anon sin filas posts (RLS)', false, SQLERRM);
  end;

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m19_post_comments;
    reset role;
    perform pg_temp.m19_val(33, 'Anon sin filas comentarios (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m19_val(33, 'Anon sin filas comentarios (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m19_val(33, 'Anon sin filas comentarios (RLS)', false, SQLERRM);
  end;

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m19_post_reactions;
    reset role;
    perform pg_temp.m19_val(34, 'Anon sin filas reacciones (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m19_val(34, 'Anon sin filas reacciones (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m19_val(34, 'Anon sin filas reacciones (RLS)', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);

  begin
    v_feed := public.m19_list_public_feed_page(null, null, null, 20, 'ALL');
    perform pg_temp.m19_val(35, 'Anon list_public_feed_page',
      jsonb_array_length(coalesce(v_feed->'items', '[]'::jsonb)) >= 1);
  exception when others then
    perform pg_temp.m19_val(35, 'Anon list_public_feed_page', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_pub);
    perform pg_temp.m19_val(36, 'Anon get_public_post', v_json is not null);
  exception when others then
    perform pg_temp.m19_val(36, 'Anon get_public_post', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_draft;
    perform pg_temp.m19_val(37, 'DRAFT oculto en feed', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_val(37, 'DRAFT oculto en feed', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_hidden;
    perform pg_temp.m19_val(38, 'HIDDEN oculto en feed', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_val(38, 'HIDDEN oculto en feed', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_blocked;
    perform pg_temp.m19_val(39, 'BLOCKED oculto en feed', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_val(39, 'BLOCKED oculto en feed', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_pending;
    perform pg_temp.m19_val(40, 'PENDING oculto en feed', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_val(40, 'PENDING oculto en feed', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_orgvis;
    perform pg_temp.m19_val(41, 'ORGANIZATION visibility oculto', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_val(41, 'ORGANIZATION visibility oculto', false, SQLERRM);
  end;

  perform pg_temp.m19_act_as(v_out);
  begin
    perform public.m19_create_post(
      v_org, 'Hack M19', 'Contenido hack post M19 test permisos.'
    );
    perform pg_temp.m19_val(42, 'Ajeno no crea post', false);
  exception when others then
    perform pg_temp.m19_val(42, 'Ajeno no crea post', SQLERRM like '%M19_PERMISSION_DENIED%');
  end;

  begin
    perform public.m19_transition_post(v_p_pub, 'HIDDEN');
    perform pg_temp.m19_val(43, 'Ajeno no transiciona post', false);
  exception when others then
    perform pg_temp.m19_val(43, 'Ajeno no transiciona post', SQLERRM like '%M19_PERMISSION_DENIED%');
  end;

  begin
    perform public.m19_list_org_posts(v_org);
    perform pg_temp.m19_val(44, 'Ajeno no lista org posts', false);
  exception when others then
    perform pg_temp.m19_val(44, 'Ajeno no lista org posts', SQLERRM like '%M19_PERMISSION_DENIED%');
  end;

  begin
    perform public.m19_create_post(
      v_bad_org, 'Pet shop post', 'Contenido org no elegible M19 test.'
    );
    perform pg_temp.m19_val(45, 'Org no elegible rechazada', false);
  exception when others then
    perform pg_temp.m19_val(45, 'Org no elegible rechazada', SQLERRM like '%M19_ORGANIZATION_NOT_ELIGIBLE%');
  end;

  perform pg_temp.m19_act_as(v_mgr);
  begin
    v_json := public.m19_create_post(
      v_org, 'Post RPC M19', 'Contenido post creado vía RPC M19 test.'
    );
    v_p_flow := (v_json->>'id')::uuid;
    perform pg_temp.m19_val(46, 'Manager crea post borrador', v_json->>'status' = 'DRAFT');
  exception when others then
    perform pg_temp.m19_val(46, 'Manager crea post borrador', false, SQLERRM);
  end;

  if v_p_flow is not null then
    begin
      v_json := public.m19_transition_post(v_p_flow, 'PUBLISHED');
      perform pg_temp.m19_val(47, 'Manager publica post', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m19_val(47, 'Manager publica post', false, SQLERRM);
    end;

    update public.m19_social_posts
    set moderation_status = 'APPROVED',
        published_at = coalesce(published_at, v_now)
    where id = v_p_flow;
  else
    perform pg_temp.m19_val(47, 'Manager publica post', false, 'prerequisite case 46 failed');
  end if;

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm19_list_public_feed_page'
    and grantee in ('anon', 'PUBLIC');
  perform pg_temp.m19_val(48, 'Grant execute feed_page anon', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm19_get_public_post'
    and grantee in ('anon', 'PUBLIC');
  perform pg_temp.m19_val(49, 'Grant execute get_public anon', v_cnt >= 1);

  perform pg_temp.m19_act_as(v_mgr);
  begin
    select count(*)::int into v_cnt from public.m19_list_org_posts(v_org);
    perform pg_temp.m19_val(50, 'Manager lista org posts', v_cnt >= 1);
  exception when others then
    perform pg_temp.m19_val(50, 'Manager lista org posts', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_post(v_p_pub);
    perform pg_temp.m19_val(51, 'Manager lee post interno',
      (v_json->>'organization_id')::uuid = v_org);
  exception when others then
    perform pg_temp.m19_val(51, 'Manager lee post interno', false, SQLERRM);
  end;

  perform pg_temp.m19_act_as(v_out);
  begin
    perform public.m19_get_post(v_p_pub);
    perform pg_temp.m19_val(52, 'Ajeno no lee post interno', false);
  exception when others then
    perform pg_temp.m19_val(52, 'Ajeno no lee post interno', SQLERRM like '%M19_PERMISSION_DENIED%');
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  update public.m19_social_posts set post_status = 'REMOVED' where id = v_p_text;
  begin
    perform public.m19_get_public_post(v_p_text);
    perform pg_temp.m19_val(53, 'REMOVED get_public falla', false);
  exception when others then
    perform pg_temp.m19_val(53, 'REMOVED get_public falla', SQLERRM like '%M19_POST_REMOVED%');
  end;
  update public.m19_social_posts
  set post_status = 'PUBLISHED', moderation_status = 'APPROVED'
  where id = v_p_text;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_arch;
    perform pg_temp.m19_val(54, 'ARCHIVED oculto en feed', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_val(54, 'ARCHIVED oculto en feed', false, SQLERRM);
  end;

  update public.m19_social_posts
  set post_status = 'REMOVED_BY_MODERATION', moderation_status = 'BLOCKED'
  where id = v_p_flow;
  begin
    perform public.m19_get_public_post(v_p_flow);
    perform pg_temp.m19_val(55, 'REMOVED_BY_MODERATION no público', false);
  exception when others then
    perform pg_temp.m19_val(55, 'REMOVED_BY_MODERATION no público', SQLERRM like '%M19_POST_REMOVED%');
  end;

  -- ========================================================================
  -- OPERACIONES 56–85
  -- ========================================================================
  perform pg_temp.m19_act_as(v_mgr);
  begin
    v_json := public.m19_create_post(
      v_org, 'Post Ops M19', 'Contenido operaciones M19 validación remota.'
    );
    v_p_bad_trans := (v_json->>'id')::uuid;
    perform pg_temp.m19_val(56, 'Create post DRAFT', v_json->>'status' = 'DRAFT');
  exception when others then
    perform pg_temp.m19_val(56, 'Create post DRAFT', false, SQLERRM);
  end;

  if v_p_bad_trans is not null then
    begin
      v_json := public.m19_transition_post(v_p_bad_trans, 'PUBLISHED');
      perform pg_temp.m19_val(57, 'Publish transition', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m19_val(57, 'Publish transition', false, SQLERRM);
    end;

    update public.m19_social_posts
    set moderation_status = 'APPROVED', published_at = coalesce(published_at, v_now)
    where id = v_p_bad_trans;

    begin
      v_json := public.m19_transition_post(v_p_bad_trans, 'PUBLISHED');
      perform pg_temp.m19_val(58, 'Publish idempotente', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m19_val(58, 'Publish idempotente', false, SQLERRM);
    end;

    begin
      v_json := public.m19_transition_post(v_p_bad_trans, 'HIDDEN');
      perform pg_temp.m19_val(59, 'Hide PUBLISHED->HIDDEN', v_json->>'status' = 'HIDDEN');
    exception when others then
      perform pg_temp.m19_val(59, 'Hide PUBLISHED->HIDDEN', false, SQLERRM);
    end;

    begin
      v_json := public.m19_archive_post(v_p_bad_trans);
      perform pg_temp.m19_val(60, 'Archive post RPC (061)', v_json->>'status' = 'ARCHIVED');
    exception when others then
      perform pg_temp.m19_val(60, 'Archive post RPC (061)', false, SQLERRM);
    end;
  else
    perform pg_temp.m19_val(57, 'Publish transition', false, 'prerequisite case 56 failed');
    perform pg_temp.m19_val(58, 'Publish idempotente', false, 'prerequisite case 56 failed');
    perform pg_temp.m19_val(59, 'Hide PUBLISHED->HIDDEN', false, 'prerequisite case 56 failed');
    perform pg_temp.m19_val(60, 'Archive post RPC (061)', false, 'prerequisite case 56 failed');
  end if;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_bad_trans;
    perform pg_temp.m19_val(61, 'ARCHIVED post fuera feed', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_val(61, 'ARCHIVED post fuera feed', false, SQLERRM);
  end;

  perform pg_temp.m19_act_as(v_mgr);
  begin
    v_json := public.m19_create_post(
      v_org, 'Salto inválido M19', 'Contenido salto inválido M19 test transición.'
    );
    perform pg_temp.m19_val(62, 'Crear post transición test', v_json->>'status' = 'DRAFT');
    v_key := v_json->>'id';
  exception when others then
    perform pg_temp.m19_val(62, 'Crear post transición test', false, SQLERRM);
  end;

  if v_key is not null then
    begin
      perform public.m19_transition_post(v_key::uuid, 'HIDDEN');
      perform pg_temp.m19_val(63, 'DRAFT->HIDDEN rechazado', false);
    exception when others then
      perform pg_temp.m19_val(63, 'DRAFT->HIDDEN rechazado', SQLERRM like '%M19_INVALID_STATE_TRANSITION%');
    end;
    delete from public.m19_social_posts where id = v_key::uuid;
    v_key := null;
  end if;

  perform pg_temp.m19_act_as(v_user2);
  begin
    v_json := public.m19_add_comment(v_p_pub, 'Comentario M19 validación remota.');
    v_c_user2 := (v_json->>'id')::uuid;
    perform pg_temp.m19_val(64, 'Add comment public post', v_json->>'content' is not null);
  exception when others then
    perform pg_temp.m19_val(64, 'Add comment public post', false, SQLERRM);
  end;

  if v_c_user2 is not null then
    begin
      v_json := public.m19_edit_comment(v_c_user2, 'Comentario editado M19 validación.');
      perform pg_temp.m19_val(65, 'Edit own comment (061)',
        v_json->>'content' = 'Comentario editado M19 validación.');
    exception when others then
      perform pg_temp.m19_val(65, 'Edit own comment (061)', false, SQLERRM);
    end;

    begin
      v_json := public.m19_archive_comment(v_c_user2);
      perform pg_temp.m19_val(66, 'Archive own comment (061)', (v_json->>'ok')::boolean = true);
    exception when others then
      perform pg_temp.m19_val(66, 'Archive own comment (061)', false, SQLERRM);
    end;
  end if;

  perform pg_temp.m19_act_as(v_out);
  begin
    insert into public.m19_post_comments (post_id, user_id, author_display_name, content, archived)
    values (v_p_pub, v_out, 'Participante', 'Comentario ajeno M19 test.', false)
    returning id into v_c_arch;
  exception when others then
    perform pg_temp.m19_val(67, 'Ajeno no edita comentario', false, SQLERRM);
    v_c_arch := null;
  end;

  if v_c_arch is not null then
    perform pg_temp.m19_act_as(v_user2);
    begin
      perform public.m19_edit_comment(v_c_arch, 'Hack comentario M19');
      perform pg_temp.m19_val(67, 'Ajeno no edita comentario', false);
    exception when others then
      perform pg_temp.m19_val(67, 'Ajeno no edita comentario', SQLERRM like '%M19_PERMISSION_DENIED%');
    end;
    delete from public.m19_post_comments where id = v_c_arch;
  end if;

  perform pg_temp.m19_act_as(v_user2);
  begin
    v_json := public.m19_add_reaction(v_p_pub, 'LOVE');
    perform pg_temp.m19_val(68, 'Add LOVE reaction (061)', v_json->>'reaction_type' = 'LOVE');
  exception when others then
    perform pg_temp.m19_val(68, 'Add LOVE reaction (061)', false, SQLERRM);
  end;

  begin
    v_json := public.m19_add_reaction(v_p_pub, 'LIKE');
    perform pg_temp.m19_val(69, 'Change reaction LOVE->LIKE', v_json->>'reaction_type' = 'LIKE');
  exception when others then
    perform pg_temp.m19_val(69, 'Change reaction LOVE->LIKE', false, SQLERRM);
  end;

  begin
    v_json := public.m19_remove_reaction(v_p_pub);
    perform pg_temp.m19_val(70, 'Remove reaction', (v_json->>'ok')::boolean = true);
  exception when others then
    perform pg_temp.m19_val(70, 'Remove reaction', false, SQLERRM);
  end;

  begin
    v_json := public.m19_add_reaction(v_p_pub, 'LOVE');
    v_json := public.m19_add_reaction(v_p_pub, 'LOVE');
    perform pg_temp.m19_val(71, 'Reaction LOVE idempotente', v_json->>'reaction_type' = 'LOVE');
  exception when others then
    perform pg_temp.m19_val(71, 'Reaction LOVE idempotente', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    v_feed := public.m19_list_public_feed_page(null, null, null, 2, 'ALL');
    perform pg_temp.m19_val(72, 'Feed pagination page 1',
      jsonb_array_length(coalesce(v_feed->'items', '[]'::jsonb)) <= 2);
  exception when others then
    perform pg_temp.m19_val(72, 'Feed pagination page 1', false, SQLERRM);
  end;

  begin
    v_cursor := v_feed->>'next_cursor';
    if v_cursor is not null then
      v_json := public.m19_list_public_feed_page(null, null, v_cursor, 2, 'ALL');
      perform pg_temp.m19_val(73, 'Feed pagination cursor page 2',
        v_json ? 'items' and (v_json->>'has_more') is not null);
    else
      perform pg_temp.m19_val(73, 'Feed pagination cursor page 2', true, 'single page only');
    end if;
  exception when others then
    perform pg_temp.m19_val(73, 'Feed pagination cursor page 2', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'PETS')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_pet;
    perform pg_temp.m19_val(74, 'Filter kind PETS', v_cnt >= 1);
  exception when others then
    perform pg_temp.m19_val(74, 'Filter kind PETS', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'MEDIA')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_media;
    perform pg_temp.m19_val(75, 'Filter kind MEDIA', v_cnt >= 1);
  exception when others then
    perform pg_temp.m19_val(75, 'Filter kind MEDIA', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'TEXT')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_pub;
    perform pg_temp.m19_val(76, 'Filter kind TEXT', v_cnt >= 1);
  exception when others then
    perform pg_temp.m19_val(76, 'Filter kind TEXT', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page('Mascota', null, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j
    where (j->>'id')::uuid = v_p_pet;
    perform pg_temp.m19_val(77, 'Search query filter', v_cnt >= 1);
  exception when others then
    perform pg_temp.m19_val(77, 'Search query filter', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, v_org, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j;
    perform pg_temp.m19_val(78, 'Filter by organization_id', v_cnt >= 1);
  exception when others then
    perform pg_temp.m19_val(78, 'Filter by organization_id', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_engagement_summary(v_p_pub);
    perform pg_temp.m19_val(79, 'Engagement summary love_count',
      v_json ? 'love_count' and (v_json->>'love_count')::int >= 0);
  exception when others then
    perform pg_temp.m19_val(79, 'Engagement summary love_count', false, SQLERRM);
  end;

  perform pg_temp.m19_act_as(v_user2);
  begin
    v_json := public.m19_add_comment(v_p_pub, 'Comentario listado M19 validación.');
    v_c_arch := (v_json->>'id')::uuid;
    select count(*)::int into v_cnt from public.m19_list_public_comments(v_p_pub);
    perform pg_temp.m19_val(80, 'List public comments', v_cnt >= 1);
  exception when others then
    perform pg_temp.m19_val(80, 'List public comments', false, SQLERRM);
  end;

  if v_c_arch is not null then
    perform pg_temp.m19_act_as(v_user2);
    perform public.m19_archive_comment(v_c_arch);
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    begin
      select count(*)::int into v_cnt from public.m19_list_public_comments(v_p_pub) c
      where (c->>'id')::uuid = v_c_arch;
      perform pg_temp.m19_val(81, 'Archived comment not listed', v_cnt = 0);
    exception when others then
      perform pg_temp.m19_val(81, 'Archived comment not listed', false, SQLERRM);
    end;
  end if;

  perform pg_temp.m19_act_as(v_mgr);
  if v_p_bad_trans is not null then
    begin
      v_json := public.m19_transition_post(v_p_bad_trans, 'PUBLISHED');
      perform pg_temp.m19_val(82, 'ARCHIVED->PUBLISHED (061)', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m19_val(82, 'ARCHIVED->PUBLISHED (061)', false, SQLERRM);
    end;

    update public.m19_social_posts
    set moderation_status = 'APPROVED', published_at = coalesce(published_at, v_now)
    where id = v_p_bad_trans;

    begin
      v_json := public.m19_transition_post(v_p_bad_trans, 'REMOVED');
      perform pg_temp.m19_val(83, 'Transition to REMOVED', v_json->>'status' = 'REMOVED');
    exception when others then
      perform pg_temp.m19_val(83, 'Transition to REMOVED', false, SQLERRM);
    end;

    begin
      perform public.m19_transition_post(v_p_bad_trans, 'PUBLISHED');
      perform pg_temp.m19_val(84, 'No reactivar REMOVED', false);
    exception when others then
      perform pg_temp.m19_val(84, 'No reactivar REMOVED', SQLERRM like '%M19_STATE_ALREADY_FINAL%');
    end;
  end if;

  begin
    perform public.m19_create_post(v_org, '', 'Contenido título inválido M19.');
    perform pg_temp.m19_val(85, 'Invalid title rejected', false);
  exception when others then
    perform pg_temp.m19_val(85, 'Invalid title rejected', SQLERRM like '%M19_INVALID_TITLE%');
  end;

  -- ========================================================================
  -- PRIVACIDAD 86–105
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);

  begin
    v_json := public.m19_get_public_post(v_p_pub);
    perform pg_temp.m19_val(86, 'Sin user_id en post público', v_json->>'user_id' is null);
  exception when others then
    perform pg_temp.m19_val(86, 'Sin user_id en post público', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_pub);
    perform pg_temp.m19_val(87, 'Sin author_user_id público', v_json->>'author_user_id' is null);
  exception when others then
    perform pg_temp.m19_val(87, 'Sin author_user_id público', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_pub);
    perform pg_temp.m19_val(88, 'Sin organization_id público', v_json->>'organization_id' is null);
  exception when others then
    perform pg_temp.m19_val(88, 'Sin organization_id público', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_pub);
    perform pg_temp.m19_val(89, 'Sin email en post público',
      v_json::text not ilike '%@%' and v_json->>'email' is null);
  exception when others then
    perform pg_temp.m19_val(89, 'Sin email en post público', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_pub);
    perform pg_temp.m19_val(90, 'Sin created_by público', v_json->>'created_by' is null);
  exception when others then
    perform pg_temp.m19_val(90, 'Sin created_by público', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_pub);
    perform pg_temp.m19_val(91, 'Sin moderation_status público', v_json->>'moderation_status' is null);
  exception when others then
    perform pg_temp.m19_val(91, 'Sin moderation_status público', false, SQLERRM);
  end;

  perform pg_temp.m19_act_as(v_user2);
  begin
    v_json := public.m19_add_comment(v_p_pub, 'Comentario privacidad M19 test.');
    perform pg_temp.m19_val(92, 'Comentario sin user_id',
      v_json->>'user_id' is null and v_json->>'author_display_name' is not null);
  exception when others then
    perform pg_temp.m19_val(92, 'Comentario sin user_id', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    v_json := public.m19_get_public_post(v_p_pet);
    perform pg_temp.m19_val(93, 'Referencias sin target_id',
      not (v_json->'content_references')::text ilike '%target_id%');
  exception when others then
    perform pg_temp.m19_val(93, 'Referencias sin target_id', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_media);
    select count(*)::int into v_cnt
    from jsonb_array_elements(coalesce(v_json->'media_attachments', '[]'::jsonb)) m
    where m->>'ref' = 'm19/private-internal.jpg';
    perform pg_temp.m19_val(94, 'Media privada excluida', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_val(94, 'Media privada excluida', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_media);
    perform pg_temp.m19_val(95, 'Cover privado oculto',
      v_json->>'cover_image_ref' = 'm19/public-cover.jpg');
  exception when others then
    perform pg_temp.m19_val(95, 'Cover privado oculto', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_array_elements(
      coalesce(public.m19_list_public_feed_page(null, null, null, 50, 'ALL')->'items', '[]'::jsonb)
    ) j
    where j::text ilike '%author_user_id%' or j::text ilike '%organization_id%';
    perform pg_temp.m19_val(96, 'Feed list sin PII ids', v_cnt = 0);
  exception when others then
    perform pg_temp.m19_val(96, 'Feed list sin PII ids', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_pub);
    perform pg_temp.m19_val(97, 'organization_display_name presente',
      v_json->>'organization_display_name' is not null);
  exception when others then
    perform pg_temp.m19_val(97, 'organization_display_name presente', false, SQLERRM);
  end;

  begin
    v_json := public._m19_public_post_json(v_p_pub);
    perform pg_temp.m19_val(98, 'love_count en JSON público', v_json ? 'love_count');
  exception when others then
    perform pg_temp.m19_val(98, 'love_count en JSON público', false, SQLERRM);
  end;

  begin
    perform public.m19_get_public_post(v_p_draft);
    perform pg_temp.m19_val(99, 'Error M19_POST_NOT_PUBLIC', false);
  exception when others then
    v_err := SQLERRM;
    perform pg_temp.m19_val(99, 'Error M19_POST_NOT_PUBLIC',
      v_err like '%M19_POST_NOT_PUBLIC%' and v_err not ilike '%@%');
  end;

  perform pg_temp.m19_act_as(v_out);
  begin
    perform public.m19_create_post(v_org, 'Hack privacidad', 'Contenido hack privacidad M19 test.');
    perform pg_temp.m19_val(100, 'Error M19_PERMISSION_DENIED', false);
  exception when others then
    v_err := SQLERRM;
    perform pg_temp.m19_val(100, 'Error M19_PERMISSION_DENIED',
      v_err like '%M19_PERMISSION_DENIED%' and v_err not ilike '%@%');
  end;

  begin
    perform public.m19_create_post(v_org, 'x', 'abc');
    perform pg_temp.m19_val(101, 'Error sin email en mensaje', false);
  exception when others then
    v_err := SQLERRM;
    perform pg_temp.m19_val(101, 'Error sin email en mensaje',
      v_err not ilike '%m19-mgr@test.local%' and v_err not ilike '%@%');
  end;

  begin
    v_json := public._m19_public_post_json(v_p_draft);
    perform pg_temp.m19_val(102, '_m19_public_post_json draft null', v_json is null);
  exception when others then
    perform pg_temp.m19_val(102, '_m19_public_post_json draft null', false, SQLERRM);
  end;

  perform pg_temp.m19_act_as(v_mgr);
  begin
    v_json := public.m19_get_post(v_p_pub);
    perform pg_temp.m19_val(103, 'Interno sí organization_id',
      (v_json->>'organization_id')::uuid = v_org);
  exception when others then
    perform pg_temp.m19_val(103, 'Interno sí organization_id', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    v_json := public.m19_get_public_post(v_p_pet);
    perform pg_temp.m19_val(104, 'Referencias públicas route_hint',
      (v_json->'content_references'->0->>'route_hint') like 'm08/pets/%'
        and (v_json->'content_references'->0->>'target_id') is null);
  exception when others then
    perform pg_temp.m19_val(104, 'Referencias públicas route_hint', false, SQLERRM);
  end;

  begin
    v_json := public.m19_get_public_post(v_p_pub);
    perform pg_temp.m19_val(105, 'visibility en público sin internals',
      v_json ? 'visibility'
        and v_json->>'visibility' = 'PUBLIC'
        and not v_json ? 'author_user_id');
  exception when others then
    perform pg_temp.m19_val(105, 'visibility en público sin internals', false, SQLERRM);
  end;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  delete from public.m19_post_reactions
  where post_id in (
    select id from public.m19_social_posts
    where organization_id in (v_org, v_bad_org)
       or id in (
         'c0000000-0000-4000-8000-000000000001',
         'c0000000-0000-4000-8000-000000000002',
         'c0000000-0000-4000-8000-000000000003',
         'c0000000-0000-4000-8000-000000000004',
         'c0000000-0000-4000-8000-000000000005',
         'c0000000-0000-4000-8000-000000000006',
         'c0000000-0000-4000-8000-000000000007',
         'c0000000-0000-4000-8000-000000000008',
         'c0000000-0000-4000-8000-000000000009',
         'c0000000-0000-4000-8000-00000000000a'
       )
  );

  delete from public.m19_post_comments
  where post_id in (
    select id from public.m19_social_posts
    where organization_id in (v_org, v_bad_org)
  );

  delete from public.m19_social_posts
  where organization_id in (v_org, v_bad_org)
     or id in (
       'c0000000-0000-4000-8000-000000000001',
       'c0000000-0000-4000-8000-000000000002',
       'c0000000-0000-4000-8000-000000000003',
       'c0000000-0000-4000-8000-000000000004',
       'c0000000-0000-4000-8000-000000000005',
       'c0000000-0000-4000-8000-000000000006',
       'c0000000-0000-4000-8000-000000000007',
       'c0000000-0000-4000-8000-000000000008',
       'c0000000-0000-4000-8000-000000000009',
       'c0000000-0000-4000-8000-00000000000a'
     );

  delete from public.organization_memberships where organization_id in (v_org, v_bad_org);
  delete from public.organizations where id in (v_org, v_bad_org);
  delete from public.users where id in (v_mgr, v_user2, v_out);
  delete from auth.users where id in (v_mgr, v_user2, v_out);
end;
$setup$;

select case_id, label, result, detail
from m19_val_results
where result = 'FAIL'
order by case_id;

select case_id, label, result, detail
from m19_val_results
order by case_id;

create table if not exists public._m19_val_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m19_val_last_failures;

insert into public._m19_val_last_failures (case_id, label, detail)
select case_id, label, detail from m19_val_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m19_val_results where result = 'FAIL' order by case_id loop
    raise warning 'M19_VAL_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m19_val_results;

commit;
