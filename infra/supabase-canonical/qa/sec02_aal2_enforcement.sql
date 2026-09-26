-- SEC-02 directed enforcement. No secrets, emails, or JWT dumped.
-- Simulates JWT claims via request.jwt.claims (same source as auth.jwt()/auth.uid()).

do $$
declare
  v_root uuid;
  v_staff uuid;
  v_aal1_denied boolean := false;
  v_aal2_perm_denied boolean := false;
  v_root_reset_protected boolean := false;
  v_oracle_closed boolean := false;
  v_auth_state jsonb;
begin
  select user_id into v_root
    from public.platform_admin_identities
   where is_root = true
   limit 1;
  if v_root is null then
    raise exception 'SEC02_NO_ROOT';
  end if;

  select i.user_id into v_staff
    from public.platform_admin_identities i
   where i.is_root = false
   limit 1;

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_root, 'role', 'authenticated', 'aal', 'aal1')::text,
    true
  );
  begin
    perform public.staff_force_password_change(coalesce(v_staff, v_root));
  exception
    when others then
      if sqlerrm like '%MFA_REQUIRED%' then
        v_aal1_denied := true;
      end if;
  end;
  begin
    perform public.canon_admin_list_species();
  exception
    when others then
      if sqlerrm like '%MFA_REQUIRED%' then
        v_aal1_denied := v_aal1_denied or true;
      end if;
  end;
  begin
    perform public.assign_platform_role(coalesce(v_staff, v_root), 'SUPPORT');
  exception
    when others then
      if sqlerrm like '%MFA_REQUIRED%' then
        v_aal1_denied := true;
      end if;
  end;

  v_auth_state := public.get_admin_auth_state();
  if v_auth_state is null
     or (v_auth_state->>'is_admin_identity') is distinct from 'true'
     or (v_auth_state->>'mfa_required') is distinct from 'true' then
    raise exception 'SEC02_AUTH_STATE_AAL1_FAILED';
  end if;

  begin
    perform public.get_admin_session();
    raise exception 'SEC02_SESSION_AAL1_LEAK';
  exception
    when others then
      if sqlerrm not like '%MFA_REQUIRED%' then
        raise;
      end if;
  end;

  -- PERSON-like JWT asking about root UUID.
  perform set_config(
    'request.jwt.claims',
    json_build_object(
      'sub', '00000000-0000-0000-0000-000000000001',
      'role', 'authenticated',
      'aal', 'aal1'
    )::text,
    true
  );
  v_oracle_closed := public._acl_is_admin(v_root) is not true;

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_root, 'role', 'authenticated', 'aal', 'aal2')::text,
    true
  );
  if public.get_admin_session()->>'aal' is distinct from 'aal2' then
    raise exception 'SEC02_SESSION_AAL2_FAILED';
  end if;

  if v_staff is not null then
    begin
      perform public.staff_reset_mfa(v_root);
    exception
      when others then
        if sqlerrm like '%ROOT_PROTECTED%' then
          v_root_reset_protected := true;
        end if;
    end;
  else
    begin
      perform public.staff_reset_mfa(v_root);
    exception
      when others then
        if sqlerrm like '%ROOT_PROTECTED%' then
          v_root_reset_protected := true;
        end if;
    end;
  end if;

  -- AAL2 without permission: pretend actor is root JWT but call as if checking
  -- FORBIDDEN still applies after AAL2 (AAL2 != admin).
  perform set_config(
    'request.jwt.claims',
    json_build_object(
      'sub', '00000000-0000-0000-0000-000000000001',
      'role', 'authenticated',
      'aal', 'aal2'
    )::text,
    true
  );
  begin
    perform public.staff_force_password_change(v_root);
  exception
    when others then
      if sqlerrm like '%FORBIDDEN%' or sqlerrm like '%NOT_AUTHENTICATED%' or sqlerrm like '%MFA_REQUIRED%' then
        v_aal2_perm_denied := true;
      end if;
  end;

  if not v_aal1_denied then
    raise exception 'SEC02_AAL1_NOT_DENIED';
  end if;
  if not v_oracle_closed then
    raise exception 'SEC02_ACL_ORACLE_OPEN';
  end if;
  if not v_root_reset_protected then
    raise exception 'SEC02_ROOT_RESET_UNPROTECTED';
  end if;
  if not v_aal2_perm_denied then
    raise exception 'SEC02_AAL2_WITHOUT_PERMISSION_ALLOWED';
  end if;
end $$;

select
  (select relrowsecurity from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = 'country_markets') as country_markets_rls,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'get_admin_auth_state') as auth_state_fns,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname like '%_sec02_impl') as wrapped_impls,
  'SEC02_ENFORCEMENT_PASS' as result;
