-- RESCUE-01 schema smoke. Staging only. No velu mutation.
-- Disposable capability row on a throwaway person if one can be created in-session.

do $$
declare
  v_table text;
  v_fn_set oid;
  v_fn_list oid;
begin
  select to_regclass('public.person_capabilities')::text into v_table;
  if v_table is null then
    raise exception 'RESCUE01_SMOKE_FAIL: person_capabilities missing';
  end if;
  select p.oid into v_fn_set
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public' and p.proname = 'canon_set_person_capability';
  select p.oid into v_fn_list
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public' and p.proname = 'canon_list_my_person_capabilities';
  if v_fn_set is null or v_fn_list is null then
    raise exception 'RESCUE01_SMOKE_FAIL: RPCs missing';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and column_name = 'account_type'
  ) then
    raise exception 'RESCUE01_SMOKE_FAIL: account_type present';
  end if;
end$$;

select
  'person_capabilities' as object,
  to_regclass('public.person_capabilities') is not null as ok;
