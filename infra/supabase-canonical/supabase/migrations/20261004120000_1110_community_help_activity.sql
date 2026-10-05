-- 1110: own economic help history.
-- Reads only the authenticated person's rows. Does not return ids.
-- Does not replace declare, confirm, or reject.

create or replace function public.canon_list_my_campaign_contributions()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'campaign_title', camp.title,
      'organization_name', coalesce(org.name, ''),
      'amount_minor', coalesce(c.amount_minor, 0),
      'currency', coalesce(c.currency, 'ARS'),
      'status', c.status,
      'created_at', c.created_at
    ) order by c.created_at desc)
    from public.donation_contributions c
    join public.donation_campaigns camp on camp.id = c.campaign_id
    left join public.organizations org on org.id = camp.organization_id
    where c.contributor_user_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_list_my_campaign_contributions() from public, anon;
grant execute on function public.canon_list_my_campaign_contributions() to authenticated;

notify pgrst, 'reload schema';
