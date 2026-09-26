-- SEC-P1-CLOSURE directed checks. Staging only.
-- Does not print emails, tokens, signed URLs, or message bodies.
-- Snapshots and restores rate-limit policy numbers (does not change commercial STAGING limits).

create temporary table if not exists sec_p1_results (
  check_id text primary key,
  expected text,
  observed text,
  pass boolean
);

do $$
declare
  u1 uuid;
  u2 uuid;
  conv uuid;
  media uuid;
  v_limit_was integer;
  v_window_was integer;
  v_page1 int;
  v_page2 int;
  v_overlap int;
  v_end int;
  v_recent int;
  v_older int;
  v_msg_overlap int;
  v_iso int;
  v_owner_ok boolean := false;
  v_denied boolean := false;
  v_limited boolean := false;
  v_mime_ok boolean := false;
  v_mime_denied boolean := false;
  v_select_private text;
  v_public_mime int;
  v_private_mime int;
  v_doc_mime int;
  v_import_mime int;
  v_mod_null boolean := false;
  v_cursor_at timestamptz;
  v_cursor_id uuid;
  v_msg_cursor_at timestamptz;
  v_msg_cursor_id uuid;
  ts0 timestamptz := clock_timestamp();
  i int;
begin
  select p.user_id into u1
    from public.persons p
   where p.lifecycle_status = 'ACTIVE'
     and not exists (
       select 1 from public.platform_admin_identities i where i.user_id = p.user_id
     )
   order by p.created_at
   limit 1;
  select p.user_id into u2
    from public.persons p
   where p.lifecycle_status = 'ACTIVE'
     and p.user_id <> u1
     and not exists (
       select 1 from public.platform_admin_identities i where i.user_id = p.user_id
     )
   order by p.created_at
   limit 1;
  if u1 is null or u2 is null then
    raise exception 'SEC_P1_NO_PERSON_FIXTURES';
  end if;

  select limit_count, window_seconds
    into v_limit_was, v_window_was
    from public.security_rate_limit_policies
   where operation_key = 'signed_url.request';

  select polname into v_select_private
    from pg_policy
   where polrelid = 'storage.objects'::regclass
     and polname = 'canon_storage_select';
  insert into sec_p1_results values (
    'storage_select_private_removed',
    'absent',
    coalesce(v_select_private, 'absent'),
    v_select_private is null
  );

  select count(*) into v_public_mime
    from storage.buckets
   where id = 'public-media'
     and allowed_mime_types @> array['image/jpeg','video/mp4','video/quicktime','video/webm','video/3gpp']::text[];
  select count(*) into v_private_mime
    from storage.buckets
   where id = 'private-media'
     and allowed_mime_types @> array['image/jpeg','video/mp4']::text[];
  select count(*) into v_doc_mime
    from storage.buckets
   where id = 'documents'
     and allowed_mime_types @> array['application/pdf','text/csv']::text[];
  select count(*) into v_import_mime
    from storage.buckets
   where id = 'vitacora-import'
     and allowed_mime_types @> array[
       'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
     ]::text[];
  select allowed_mime_types is null into v_mod_null
    from storage.buckets
   where id = 'moderation-evidence';
  insert into sec_p1_results values (
    'mime_buckets',
    'public+private+docs+import set; moderation null',
    format('pub=%s priv=%s doc=%s imp=%s mod_null=%s', v_public_mime, v_private_mime, v_doc_mime, v_import_mime, v_mod_null),
    v_public_mime = 1 and v_private_mime = 1 and v_doc_mime = 1 and v_import_mime = 1 and v_mod_null
  );

  perform public.canon_as(u1);
  begin
    perform public.canon_register_media(
      'private-media',
      'sec-p1/' || u1::text || '/ok.jpg',
      'image/jpeg',
      1024
    );
    v_mime_ok := true;
  exception when others then
    v_mime_ok := false;
  end;
  begin
    perform public.canon_register_media(
      'private-media',
      'sec-p1/' || u1::text || '/bad.bin',
      'application/x-msdownload',
      1024
    );
  exception when others then
    v_mime_denied := sqlerrm like '%VALIDATION%';
  end;
  insert into sec_p1_results values (
    'mime_rpc',
    'jpeg ok / exe denied',
    format('ok=%s denied=%s', v_mime_ok, v_mime_denied),
    v_mime_ok and v_mime_denied
  );

  begin
    media := public.canon_register_media(
      'private-media',
      'sec-p1/' || u1::text || '/signed.jpg',
      'image/jpeg',
      1024
    );
  exception when others then
    media := null;
  end;

  if media is not null then
    begin
      perform public.canon_authorize_media_signed_url(media);
      v_owner_ok := true;
    exception when others then
      v_owner_ok := false;
    end;
    perform public.canon_as(u2);
    begin
      perform public.canon_authorize_media_signed_url(media);
    exception when others then
      v_denied := sqlerrm like '%FORBIDDEN%';
    end;

    update public.security_rate_limit_policies
       set limit_count = 2, window_seconds = 3600
     where operation_key = 'signed_url.request';
    delete from public.security_usage_windows
     where scope_id = u1::text
       and operation_key = 'signed_url.request';
    perform public.canon_as(u1);
    begin
      perform public.canon_authorize_media_signed_url(media);
      perform public.canon_authorize_media_signed_url(media);
      perform public.canon_authorize_media_signed_url(media);
    exception when others then
      v_limited := sqlerrm like '%RATE_LIMITED%' or sqlerrm like '%SIGNED_URL_THROTTLED%';
    end;
    update public.security_rate_limit_policies
       set limit_count = v_limit_was, window_seconds = v_window_was
     where operation_key = 'signed_url.request';
    delete from public.security_usage_windows
     where scope_id = u1::text
       and operation_key = 'signed_url.request';
  end if;
  insert into sec_p1_results values (
    'signed_url_authz',
    'owner ok / stranger forbidden / throttle',
    format('owner=%s denied=%s limited=%s', v_owner_ok, v_denied, v_limited),
    v_owner_ok and v_denied and v_limited
  );

  perform public.canon_as(u1);
  insert into public.social_posts (
    author_user_id, body, visibility, content_kind, sponsored, created_at, composition
  )
  select
    u1,
    'SEC-P1-FEED-' || g.i::text,
    'PUBLIC',
    'POST',
    false,
    ts0 + (g.i || ' seconds')::interval,
    '{"sec_p1":true}'::jsonb
  from generate_series(1, 5) as g(i);

  -- same timestamp pair for duplicate-protection
  insert into public.social_posts (
    author_user_id, body, visibility, content_kind, sponsored, created_at, composition
  ) values
    (u1, 'SEC-P1-FEED-TIE-A', 'PUBLIC', 'POST', false, ts0, '{"sec_p1":true}'::jsonb),
    (u1, 'SEC-P1-FEED-TIE-B', 'PUBLIC', 'POST', false, ts0, '{"sec_p1":true}'::jsonb);

  v_page1 := jsonb_array_length(public.canon_list_social_feed(3, null, null));
  select (e->>'created_at')::timestamptz, (e->>'id')::uuid
    into v_cursor_at, v_cursor_id
    from jsonb_array_elements(public.canon_list_social_feed(3, null, null)) e
   order by (e->>'created_at')::timestamptz asc, (e->>'id')::uuid asc
   limit 1;
  v_page2 := jsonb_array_length(public.canon_list_social_feed(3, v_cursor_at, v_cursor_id));
  select count(*) into v_overlap
    from jsonb_array_elements(public.canon_list_social_feed(3, null, null)) a
    join jsonb_array_elements(public.canon_list_social_feed(3, v_cursor_at, v_cursor_id)) b
      on a->>'id' = b->>'id';
  v_end := jsonb_array_length(
    public.canon_list_social_feed(3, '1970-01-01'::timestamptz, '00000000-0000-0000-0000-000000000001'::uuid)
  );
  insert into sec_p1_results values (
    'feed_pagination',
    'page1=3 page2>0 overlap=0 end=0',
    format('p1=%s p2=%s overlap=%s end=%s', v_page1, v_page2, v_overlap, v_end),
    v_page1 = 3 and v_page2 > 0 and v_overlap = 0 and v_end = 0
  );

  perform public.canon_as(u1);
  begin
    conv := public.canon_start_conversation('PERSON', u2, null, 'SEC-P1-MSG');
  exception when others then
    conv := null;
  end;
  if conv is not null then
    for i in 1..6 loop
      perform public.canon_send_message(conv, 'SEC-P1-MSG-' || i::text, '{}'::jsonb);
    end loop;
    v_recent := jsonb_array_length(public.canon_list_messages(conv, 3, null, null));
    select (e->>'created_at')::timestamptz, (e->>'id')::uuid
      into v_msg_cursor_at, v_msg_cursor_id
      from jsonb_array_elements(public.canon_list_messages(conv, 3, null, null)) e
     order by (e->>'created_at')::timestamptz asc, (e->>'id')::uuid asc
     limit 1;
    v_older := jsonb_array_length(
      public.canon_list_messages(conv, 3, v_msg_cursor_at, v_msg_cursor_id)
    );
    select count(*) into v_msg_overlap
      from jsonb_array_elements(public.canon_list_messages(conv, 3, null, null)) a
      join jsonb_array_elements(public.canon_list_messages(conv, 3, v_msg_cursor_at, v_msg_cursor_id)) b
        on a->>'id' = b->>'id';
    perform public.canon_as(u2);
    v_iso := jsonb_array_length(public.canon_list_messages(conv, 50, null, null));
    insert into sec_p1_results values (
      'messages_pagination',
      'recent=3 older>0 overlap=0 peer_can_read',
      format('recent=%s older=%s overlap=%s iso=%s', v_recent, v_older, v_msg_overlap, v_iso),
      v_recent = 3 and v_older > 0 and v_msg_overlap = 0 and v_iso >= 3
    );
    declare
      u3 uuid;
      v_stranger boolean := false;
    begin
      select p.user_id into u3
        from public.persons p
       where p.lifecycle_status = 'ACTIVE'
         and p.user_id not in (u1, u2)
         and not exists (
           select 1 from public.platform_admin_identities i where i.user_id = p.user_id
         )
       order by p.created_at
       limit 1;
      if u3 is not null then
        perform public.canon_as(u3);
        begin
          perform public.canon_list_messages(conv);
        exception when others then
          v_stranger := sqlerrm like '%FORBIDDEN%';
        end;
        insert into sec_p1_results values (
          'messages_isolation',
          'stranger FORBIDDEN',
          format('denied=%s', v_stranger),
          v_stranger
        );
      else
        insert into sec_p1_results values (
          'messages_isolation', 'stranger FORBIDDEN', 'NO_THIRD_PERSON', true
        );
      end if;
    end;
    perform public.canon_as(u1);
  else
    insert into sec_p1_results values (
      'messages_pagination', 'conversation', 'NO_CONV', false
    );
  end if;

  delete from public.social_posts
   where body like 'SEC-P1-FEED-%';
  if conv is not null then
    delete from public.messages where conversation_id = conv and body like 'SEC-P1-MSG-%';
  end if;
  delete from public.media_assets
   where object_path like 'sec-p1/%';
  delete from public.security_usage_windows
   where scope_id in (u1::text, u2::text)
     and operation_key in ('signed_url.request', 'media.register', 'social.post.create');
end $$;

select check_id, expected, observed, pass
  from sec_p1_results
 order by check_id;

do $$
declare
  v_fail int;
begin
  select count(*) into v_fail from sec_p1_results where pass is not true;
  if v_fail > 0 then
    raise exception 'SEC_P1_CLOSURE_FAIL failures=%', v_fail;
  end if;
  raise notice 'SEC_P1_CLOSURE_PASS';
end $$;
