-- SEC-03 directed abuse tests. Staging only. Cleans SEC03 fixtures only.
-- Does not print emails, tokens, signed URLs, or message bodies.

do $$
declare
  u1 uuid;
  u2 uuid;
  admin_uid uuid;
  v_post uuid;
  v_ok int := 0;
  v_limited boolean := false;
  v_u2_ok boolean := false;
  v_direct_denied boolean := false;
  v_policy_denied boolean := false;
  v_fake_key boolean := false;
  v_quota boolean := false;
  v_signed boolean := false;
  v_flag boolean := false;
  v_aal1 boolean := false;
  v_err text;
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
    raise exception 'SEC03_NO_PERSON_FIXTURES';
  end if;

  update public.security_rate_limit_policies
     set limit_count = 2, window_seconds = 3600
   where operation_key in (
     'social.post.create',
     'signed_url.request',
     'media.upload.bytes.daily'
   );

  delete from public.security_usage_windows
   where scope_id in (u1::text, u2::text)
     and operation_key in (
       'social.post.create',
       'signed_url.request',
       'media.upload.bytes.daily',
       'media.register'
     );

  perform public.canon_as(u1);
  v_post := public.canon_create_social_post('SEC03-FIXTURE-DO-NOT-KEEP A', 'PRIVATE');
  v_ok := v_ok + 1;
  v_post := public.canon_create_social_post('SEC03-FIXTURE-DO-NOT-KEEP B', 'PRIVATE');
  v_ok := v_ok + 1;
  begin
    perform public.canon_create_social_post('SEC03-FIXTURE-DO-NOT-KEEP C', 'PRIVATE');
  exception when others then
    v_err := sqlerrm;
    v_limited := v_err like '%RATE_LIMITED%';
  end;

  perform public.canon_as(u2);
  begin
    perform public.canon_create_social_post('SEC03-FIXTURE-DO-NOT-KEEP U2', 'PRIVATE');
    v_u2_ok := true;
  exception when others then
    v_u2_ok := false;
  end;

  begin
    execute 'set role authenticated';
    insert into public.social_posts (author_user_id, body, visibility, content_kind)
    values (u2, 'SEC03-FIXTURE-DIRECT-BYPASS', 'PRIVATE', 'POST');
    execute 'reset role';
  exception when others then
    execute 'reset role';
    v_direct_denied := true;
  end;

  begin
    execute 'set role authenticated';
    update public.security_rate_limit_policies
       set limit_count = 999999
     where operation_key = 'social.post.create';
    if found then
      execute 'reset role';
    else
      execute 'reset role';
      v_policy_denied := true;
    end if;
  exception when others then
    execute 'reset role';
    v_policy_denied := true;
  end;

  begin
    perform public.security_consume_rate_limit('not.a.real.operation', 1);
  exception when others then
    v_fake_key := sqlerrm like '%VALIDATION%';
  end;

  perform public.canon_as(u1);
  begin
    perform public._canon_consume_rate_limit('media.upload.bytes.daily', 209715201);
  exception when others then
    v_quota := sqlerrm like '%QUOTA_EXCEEDED%';
  end;

  begin
    perform public._canon_consume_rate_limit('signed_url.request', 1);
    perform public._canon_consume_rate_limit('signed_url.request', 1);
    perform public._canon_consume_rate_limit('signed_url.request', 1);
  exception when others then
    v_signed := sqlerrm like '%RATE_LIMITED%';
  end;

  update public.security_feature_flags
     set enabled = false
   where flag_key = 'imports.enabled';
  begin
    perform public.canon_import_analyze(
      '00000000-0000-0000-0000-000000000001'::uuid,
      '{}'::jsonb
    );
  exception when others then
    v_flag := sqlerrm like '%FEATURE_TEMPORARILY_DISABLED%';
  end;
  update public.security_feature_flags
     set enabled = true
   where flag_key = 'imports.enabled';

  select i.user_id into admin_uid
    from public.platform_admin_identities i
   where not i.is_root
     and i.disabled_at is null
   order by i.created_at
   limit 1;
  if admin_uid is not null then
    perform public.canon_as(admin_uid);
    begin
      perform public.get_admin_session();
    exception when others then
      v_aal1 := sqlerrm like '%MFA_REQUIRED%';
    end;
  end if;

  delete from public.social_posts
   where body like 'SEC03-FIXTURE-%';
  delete from public.security_usage_windows
   where scope_id in (u1::text, u2::text)
     and operation_key in (
       'social.post.create',
       'signed_url.request',
       'media.upload.bytes.daily',
       'media.register',
       'vitacora.import.analyze'
     );
  update public.security_rate_limit_policies
     set limit_count = case operation_key
       when 'social.post.create' then 10
       when 'signed_url.request' then 60
       when 'media.upload.bytes.daily' then 209715200
     end
   where operation_key in (
     'social.post.create',
     'signed_url.request',
     'media.upload.bytes.daily'
   );

  if v_ok <> 2 or not v_limited or not v_u2_ok or not v_direct_denied
     or not v_policy_denied or not v_fake_key or not v_quota
     or not v_signed or not v_flag then
    raise exception 'SEC03_ABUSE_FAIL ok=% limited=% u2=% direct=% policy=% fake=% quota=% signed=% flag=% aal1=%',
      v_ok, v_limited, v_u2_ok, v_direct_denied, v_policy_denied, v_fake_key,
      v_quota, v_signed, v_flag, v_aal1;
  end if;

  raise notice 'SEC03_ABUSE_PASS aal1_admin=%', v_aal1;
end $$;
