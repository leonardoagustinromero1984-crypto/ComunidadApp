-- 1085: Campaign contributions declared outside LeoVer, confirmed by the creator.
-- No checkout, wallet, or payment processor. Does not edit 1017/1024/054.

alter table public.donation_contributions
  add column if not exists status text,
  add column if not exists note text,
  add column if not exists confirmed_at timestamptz,
  add column if not exists confirmed_by uuid,
  add column if not exists currency text,
  add column if not exists amount_minor bigint;

update public.donation_contributions
   set status = coalesce(nullif(btrim(status), ''), 'CONFIRMED')
 where status is null;

update public.donation_contributions
   set currency = coalesce(nullif(btrim(currency), ''), 'ARS')
 where currency is null;

update public.donation_contributions
   set amount_minor = coalesce(amount_minor, round(coalesce(amount, 0) * 100)::bigint)
 where amount_minor is null;

alter table public.donation_contributions
  alter column status set default 'PENDING';

alter table public.donation_contributions
  alter column currency set default 'ARS';

do $$
begin
  if not exists (
    select 1 from pg_constraint
     where conname = 'donation_contributions_status_chk'
  ) then
    alter table public.donation_contributions
      add constraint donation_contributions_status_chk
      check (status in ('PENDING', 'CONFIRMED', 'REJECTED'));
  end if;
end $$;

create or replace function public._canon_campaign_is_manager(p_campaign_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
      from public.donation_campaigns d
     where d.id = p_campaign_id
       and d.created_by = auth.uid()
  );
$$;

create or replace function public.canon_get_donation_campaign(p_campaign_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v jsonb;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select jsonb_build_object(
    'id', d.id,
    'title', d.title,
    'description', coalesce(d.title, ''),
    'alias_cbu', d.alias_cbu,
    'payment_alias', d.alias_cbu,
    'status', d.status,
    'organization_id', d.organization_id,
    'created_by', d.created_by,
    'confirmed_amount_minor', coalesce((
      select sum(c.amount_minor) from public.donation_contributions c
       where c.campaign_id = d.id and c.status = 'CONFIRMED'
    ), 0),
    'confirmed_contribution_count', coalesce((
      select count(*) from public.donation_contributions c
       where c.campaign_id = d.id and c.status = 'CONFIRMED'
    ), 0),
    'pending_contribution_count', coalesce((
      select count(*) from public.donation_contributions c
       where c.campaign_id = d.id and c.status = 'PENDING'
    ), 0),
    'currency', 'ARS'
  )
    into v
    from public.donation_campaigns d
   where d.id = p_campaign_id;
  if v is null then raise exception 'NOT_FOUND'; end if;
  return v;
end;
$$;

create or replace function public.canon_declare_campaign_contribution(
  p_campaign_id uuid,
  p_amount_minor bigint,
  p_note text default null,
  p_currency text default 'ARS'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_status text;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_campaign_id is null or coalesce(p_amount_minor, 0) < 1 then
    raise exception 'VALIDATION';
  end if;
  select status into v_status from public.donation_campaigns where id = p_campaign_id;
  if v_status is null then raise exception 'NOT_FOUND'; end if;
  if v_status <> 'OPEN' then raise exception 'FORBIDDEN'; end if;
  insert into public.donation_contributions (
    campaign_id, contributor_user_id, transfer_reference, amount, amount_minor,
    currency, status, note
  ) values (
    p_campaign_id,
    auth.uid(),
    'ext-' || gen_random_uuid()::text,
    (p_amount_minor::numeric / 100),
    p_amount_minor,
    upper(btrim(coalesce(p_currency, 'ARS'))),
    'PENDING',
    nullif(btrim(coalesce(p_note, '')), '')
  )
  returning id into v_id;
  return jsonb_build_object(
    'id', v_id,
    'campaign_id', p_campaign_id,
    'amount_minor', p_amount_minor,
    'currency', upper(btrim(coalesce(p_currency, 'ARS'))),
    'status', 'PENDING'
  );
end;
$$;

create or replace function public.canon_confirm_campaign_contribution(p_contribution_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v public.donation_contributions%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v from public.donation_contributions where id = p_contribution_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if not public._canon_campaign_is_manager(v.campaign_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v.status = 'CONFIRMED' then
    return jsonb_build_object('id', v.id, 'status', v.status, 'amount_minor', v.amount_minor);
  end if;
  if v.status <> 'PENDING' then raise exception 'VALIDATION'; end if;
  update public.donation_contributions
     set status = 'CONFIRMED',
         confirmed_at = timezone('utc', now()),
         confirmed_by = auth.uid()
   where id = p_contribution_id
     and status = 'PENDING';
  return jsonb_build_object('id', p_contribution_id, 'status', 'CONFIRMED', 'amount_minor', v.amount_minor);
end;
$$;

create or replace function public.canon_reject_campaign_contribution(p_contribution_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v public.donation_contributions%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v from public.donation_contributions where id = p_contribution_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if not public._canon_campaign_is_manager(v.campaign_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v.status = 'REJECTED' then
    return jsonb_build_object('id', v.id, 'status', v.status);
  end if;
  if v.status <> 'PENDING' then raise exception 'VALIDATION'; end if;
  update public.donation_contributions
     set status = 'REJECTED',
         confirmed_at = timezone('utc', now()),
         confirmed_by = auth.uid()
   where id = p_contribution_id
     and status = 'PENDING';
  return jsonb_build_object('id', p_contribution_id, 'status', 'REJECTED');
end;
$$;

create or replace function public.canon_list_campaign_contributions(p_campaign_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._canon_campaign_is_manager(p_campaign_id)
     and not exists (
       select 1 from public.donation_contributions c
        where c.campaign_id = p_campaign_id and c.contributor_user_id = auth.uid()
     )
  then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id,
      'campaign_id', c.campaign_id,
      'amount_minor', coalesce(c.amount_minor, 0),
      'currency', coalesce(c.currency, 'ARS'),
      'status', c.status,
      'note', c.note,
      'created_at', c.created_at,
      'confirmed_at', c.confirmed_at,
      'mine', c.contributor_user_id = auth.uid()
    ) order by c.created_at desc)
      from public.donation_contributions c
     where c.campaign_id = p_campaign_id
       and (
         public._canon_campaign_is_manager(p_campaign_id)
         or c.contributor_user_id = auth.uid()
       )
  ), '[]'::jsonb);
end;
$$;

revoke all on function public._canon_campaign_is_manager(uuid) from public, anon, authenticated;
revoke all on function public.canon_get_donation_campaign(uuid) from public, anon;
revoke all on function public.canon_declare_campaign_contribution(uuid, bigint, text, text) from public, anon;
revoke all on function public.canon_confirm_campaign_contribution(uuid) from public, anon;
revoke all on function public.canon_reject_campaign_contribution(uuid) from public, anon;
revoke all on function public.canon_list_campaign_contributions(uuid) from public, anon;

grant execute on function public.canon_get_donation_campaign(uuid) to authenticated;
grant execute on function public.canon_declare_campaign_contribution(uuid, bigint, text, text) to authenticated;
grant execute on function public.canon_confirm_campaign_contribution(uuid) to authenticated;
grant execute on function public.canon_reject_campaign_contribution(uuid) to authenticated;
grant execute on function public.canon_list_campaign_contributions(uuid) to authenticated;

notify pgrst, 'reload schema';
