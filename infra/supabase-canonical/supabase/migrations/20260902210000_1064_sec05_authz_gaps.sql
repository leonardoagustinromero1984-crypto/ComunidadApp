-- 1064: SEC-05 defensive authorization gaps. Does not edit 1057–1063.
-- 1) AAL2 on remaining staff moderation/search RPCs (permission checks stay).
-- 2) Actor checks on VitaCora revoke/hide/proposal/vet-care writes.

create or replace function public.canon_revoke_vitacora(p_grant_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  g public.vitacora_access_grants%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_grant_id is null then raise exception 'VALIDATION'; end if;
  select * into g from public.vitacora_access_grants where id = p_grant_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if not public._acl_pet_permission(auth.uid(), g.pet_id, 'vitacora.share') then
    raise exception 'FORBIDDEN';
  end if;
  update public.vitacora_access_grants
    set revoked_at = timezone('utc', now()), revoked_by = auth.uid()
   where id = p_grant_id and revoked_at is null;
end;
$$;

revoke all on function public.canon_revoke_vitacora(uuid) from public, anon;
grant execute on function public.canon_revoke_vitacora(uuid) to authenticated;

create or replace function public.canon_hide_integration(p_link_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pet uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_link_id is null then raise exception 'VALIDATION'; end if;
  select pet_id into v_pet from public.vitacora_integration_links where id = p_link_id;
  if v_pet is null then raise exception 'NOT_FOUND'; end if;
  if not public._acl_pet_permission(auth.uid(), v_pet, 'vitacora.manage') then
    raise exception 'FORBIDDEN';
  end if;
  update public.vitacora_integration_links
     set visible = false, hidden_at = timezone('utc', now())
   where id = p_link_id;
end;
$$;

revoke all on function public.canon_hide_integration(uuid) from public, anon;
grant execute on function public.canon_hide_integration(uuid) to authenticated;

create or replace function public.canon_create_proposal(
  p_pet_id uuid, p_origin text, p_payload jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_pet_id is null then raise exception 'VALIDATION'; end if;
  if not (
    public._acl_pet_holder(auth.uid(), p_pet_id)
    or public._acl_grant_active(auth.uid(), p_pet_id, 'ESSENTIAL')
    or public._acl_grant_active(auth.uid(), p_pet_id, 'HEALTH')
  ) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.vitacora_update_proposals (pet_id, origin_kind, payload, actor_user_id)
  values (p_pet_id, p_origin, p_payload, auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

revoke all on function public.canon_create_proposal(uuid, text, jsonb) from public, anon;
grant execute on function public.canon_create_proposal(uuid, text, jsonb) to authenticated;

create or replace function public.canon_record_vet_care(
  p_pet uuid, p_org uuid, p_summary text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_prof uuid;
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select id into v_prof from public.professional_profiles where person_id = auth.uid();
  if v_prof is null then raise exception 'NOT_A_VETERINARY_PROFESSIONAL'; end if;
  if not public._acl_grant_active(auth.uid(), p_pet, 'HEALTH') then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.veterinary_care_records (
    pet_id, actor_user_id, professional_profile_id, organization_id, summary, care_on
  ) values (p_pet, auth.uid(), v_prof, p_org, p_summary, current_date)
  returning id into v_id;
  return v_id;
end;
$$;

revoke all on function public.canon_record_vet_care(uuid, uuid, text) from public, anon;
grant execute on function public.canon_record_vet_care(uuid, uuid, text) to authenticated;

-- Remaining staff RPCs from 1055 that still lacked AAL2.
do $$
declare
  r record;
  impl_name text;
  call_args text;
  create_sql text;
  ret_clause text;
  ident text;
  args text;
begin
  for r in
    select p.oid,
           p.proname,
           p.pronargs,
           p.proargnames,
           p.proargmodes
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.proname not like '%\_sec05\_impl' escape '\'
       and p.proname in (
         'list_moderation_queue',
         'get_moderation_report_for_staff',
         'triage_content_report',
         'admin_search_users',
         'admin_get_user_roles',
         'admin_get_user_status_history'
       )
  loop
    ident := pg_get_function_identity_arguments(r.oid);
    args := pg_get_function_arguments(r.oid);
    ret_clause := pg_get_function_result(r.oid);
    impl_name := r.proname || '_sec05_impl';
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
          perform public._canon_require_admin_aal2();
          perform public.%I(%s);
        end;
        $body$;
        $f$, r.proname, args, impl_name, call_args
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
          perform public._canon_require_admin_aal2();
          return query select * from public.%I(%s);
        end;
        $body$;
        $f$, r.proname, args, ret_clause, impl_name, call_args
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
          perform public._canon_require_admin_aal2();
          return public.%I(%s);
        end;
        $body$;
        $f$, r.proname, args, ret_clause, impl_name, call_args
      );
    end if;
    execute create_sql;
    execute format('revoke all on function public.%I(%s) from public, anon, authenticated', impl_name, ident);
    execute format('revoke all on function public.%I(%s) from public, anon', r.proname, ident);
    execute format('grant execute on function public.%I(%s) to authenticated', r.proname, ident);
  end loop;
end $$;

notify pgrst, 'reload schema';
