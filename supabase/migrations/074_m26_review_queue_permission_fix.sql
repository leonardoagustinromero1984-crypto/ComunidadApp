-- LeoVer M26 — migración 074: cola de revisión exige rol revisor (forward-only).
begin;

create or replace function public.m26_list_review_queue()
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated();
begin
  if not public._m26_is_reviewer(v_actor) then
    raise exception 'M26_PERMISSION_DENIED';
  end if;
  return query
  select public._m26_review_queue_json(r)
  from public.m26_ai_results r
  where r.status = 'PENDING_REVIEW'
  order by r.created_at asc;
end;
$$;

revoke all on function public.m26_list_review_queue() from public, anon;
grant execute on function public.m26_list_review_queue() to authenticated;

commit;
