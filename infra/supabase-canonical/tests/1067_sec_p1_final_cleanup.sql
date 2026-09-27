-- SEC-P1-FINAL-CLEANUP minimal revalidation. Staging only.
-- No burst. No private payloads. No emails/tokens/URLs.

select
  p.operation_key,
  p.limit_count,
  p.window_seconds,
  p.fail_closed,
  p.scope_kind
  from public.security_rate_limit_policies p
 where p.operation_key = 'signed_url.request';

select
  (select count(*) from pg_policy
    where polrelid = 'storage.objects'::regclass
      and polname = 'canon_storage_select') as legacy_private_select,
  (select count(*) from pg_policy
    where polrelid = 'storage.objects'::regclass
      and polname = 'canon_storage_select_public_media') as public_select,
  (select count(*) from pg_policy
    where polrelid = 'storage.objects'::regclass
      and polname = 'canon_storage_insert'
      and pg_get_expr(polwithcheck, polrelid) like '%moderation-evidence%') as evidence_in_client_insert,
  (select allowed_mime_types is null from storage.buckets where id = 'moderation-evidence') as evidence_mime_null;

do $$
declare
  v_limit bigint;
  v_window integer;
  v_fail boolean;
  v_legacy int;
begin
  select limit_count, window_seconds, fail_closed
    into v_limit, v_window, v_fail
    from public.security_rate_limit_policies
   where operation_key = 'signed_url.request';
  if v_limit is distinct from 60 or v_window is distinct from 600 or v_fail is not true then
    raise exception 'SEC_P1_FINAL_SIGNED_URL_POLICY_FAIL limit=% window=% fail_closed=%',
      v_limit, v_window, v_fail;
  end if;
  select count(*) into v_legacy
    from pg_policy
   where polrelid = 'storage.objects'::regclass
     and polname = 'canon_storage_select';
  if v_legacy <> 0 then
    raise exception 'SEC_P1_FINAL_PRIVATE_SELECT_STILL_PRESENT';
  end if;
  raise notice 'SEC_P1_FINAL_CLEANUP_PASS';
end $$;
