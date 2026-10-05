-- 1112: canonical volunteering.
-- Not executed by this change.
--
-- volunteer_offers stays a person-originated offer: offered_by is required and
-- the row has no organization, no capacity and no applications. Turning that
-- row into a shelter call would repeat the mistake of showing a person offer
-- as an organization publication.
-- community_events stays in Eventos/M18. This migration does not read it and
-- does not invent opportunity_type = EVENTS.
--
-- One domain, one identifier:
--   volunteer_opportunities.id
--   is the id of the list, the detail, the application.opportunity_id
--   and the organization applicant list.

create table if not exists public.volunteer_opportunities (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  created_by uuid not null references public.persons(user_id),
  title text not null check (char_length(title) between 3 and 120),
  description text not null check (char_length(description) between 1 and 2000),
  opportunity_type text not null default 'OTHER'
    check (opportunity_type in (
      'SHELTER_SUPPORT', 'ANIMAL_CARE', 'TRANSPORT', 'FUNDRAISING',
      'PHOTOGRAPHY', 'ADMINISTRATIVE', 'CONSTRUCTION',
      'PROFESSIONAL_SUPPORT', 'OTHER'
    )),
  slots_needed integer not null check (slots_needed > 0),
  status text not null default 'PUBLISHED'
    check (status in ('PUBLISHED', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);

create index if not exists volunteer_opportunities_org_status_idx
  on public.volunteer_opportunities (organization_id, status, created_at desc);

create table if not exists public.volunteer_applications (
  id uuid primary key default gen_random_uuid(),
  opportunity_id uuid not null references public.volunteer_opportunities(id) on delete cascade,
  applicant_user_id uuid not null references public.persons(user_id),
  message text null check (message is null or char_length(message) <= 1000),
  status text not null default 'SUBMITTED'
    check (status in ('SUBMITTED', 'ACCEPTED', 'WITHDRAWN')),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  unique (opportunity_id, applicant_user_id)
);

create index if not exists volunteer_applications_opportunity_idx
  on public.volunteer_applications (opportunity_id, status);

alter table public.volunteer_opportunities enable row level security;
alter table public.volunteer_applications enable row level security;
revoke all on table public.volunteer_opportunities from public, anon, authenticated;
revoke all on table public.volunteer_applications from public, anon, authenticated;

create or replace function public._canon_volunteer_is_manager(p_opportunity_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
      from public.volunteer_opportunities o
     where o.id = p_opportunity_id
       and public._acl_org_permission(auth.uid(), o.organization_id, 'org.edit')
  );
$$;

revoke all on function public._canon_volunteer_is_manager(uuid) from public, anon, authenticated;

create or replace function public._canon_volunteer_opportunity_json(p_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', o.id,
    'organization_id', o.organization_id,
    'organization_display_name', org.name,
    'title', o.title,
    'description', o.description,
    'opportunity_type', o.opportunity_type,
    'status', o.status,
    'slots_needed', o.slots_needed,
    'slots_filled', coalesce((
      select count(*)::int
        from public.volunteer_applications a
       where a.opportunity_id = o.id
         and a.status = 'ACCEPTED'
    ), 0),
    'created_at', o.created_at
  )
  from public.volunteer_opportunities o
  join public.organizations org on org.id = o.organization_id
  where o.id = p_id;
$$;

revoke all on function public._canon_volunteer_opportunity_json(uuid) from public, anon, authenticated;

create or replace function public.canon_list_volunteer_opportunities(p_organization_id uuid default null)
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
    select jsonb_agg(public._canon_volunteer_opportunity_json(o.id) order by o.created_at desc)
      from public.volunteer_opportunities o
     where o.status = 'PUBLISHED'
       and (p_organization_id is null or o.organization_id = p_organization_id)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_volunteer_opportunity(p_opportunity_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_status text;
  v jsonb;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  select o.status into v_status
    from public.volunteer_opportunities o
   where o.id = p_opportunity_id;
  if v_status is null then
    raise exception 'OPPORTUNITY_NOT_FOUND';
  end if;
  if v_status <> 'PUBLISHED' and not public._canon_volunteer_is_manager(p_opportunity_id) then
    raise exception 'OPPORTUNITY_NOT_FOUND';
  end if;
  v := public._canon_volunteer_opportunity_json(p_opportunity_id);
  return v || jsonb_build_object(
    'can_manage', public._canon_volunteer_is_manager(p_opportunity_id)
  );
end;
$$;

create or replace function public.canon_apply_volunteer_opportunity(
  p_opportunity_id uuid,
  p_message text default null
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
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  select o.status into v_status
    from public.volunteer_opportunities o
   where o.id = p_opportunity_id;
  if v_status is distinct from 'PUBLISHED' then
    raise exception 'OPPORTUNITY_NOT_FOUND';
  end if;
  insert into public.volunteer_applications (
    opportunity_id, applicant_user_id, message, status
  ) values (
    p_opportunity_id, auth.uid(), nullif(btrim(p_message), ''), 'SUBMITTED'
  )
  on conflict (opportunity_id, applicant_user_id) do update
    set message = excluded.message,
        status = 'SUBMITTED',
        updated_at = timezone('utc', now())
    where public.volunteer_applications.status = 'WITHDRAWN'
  returning id, status into v_id, v_status;
  if v_id is null then
    select a.id, a.status into v_id, v_status
      from public.volunteer_applications a
     where a.opportunity_id = p_opportunity_id
       and a.applicant_user_id = auth.uid();
  end if;
  return jsonb_build_object(
    'id', v_id,
    'opportunity_id', p_opportunity_id,
    'applicant_user_id', auth.uid(),
    'status', v_status,
    'message', nullif(btrim(p_message), '')
  );
end;
$$;

create or replace function public.canon_list_volunteer_applicants(p_opportunity_id uuid)
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
  if not public._canon_volunteer_is_manager(p_opportunity_id) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', a.id,
      'opportunity_id', a.opportunity_id,
      'applicant_user_id', a.applicant_user_id,
      'message', a.message,
      'status', a.status,
      'created_at', a.created_at
    ) order by a.created_at)
      from public.volunteer_applications a
     where a.opportunity_id = p_opportunity_id
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_my_volunteer_applications()
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
      'id', a.id,
      'opportunity_id', a.opportunity_id,
      'opportunity_title', o.title,
      'organization_name', org.name,
      'applicant_user_id', a.applicant_user_id,
      'status', a.status,
      'created_at', a.created_at
    ) order by a.created_at desc)
      from public.volunteer_applications a
      join public.volunteer_opportunities o on o.id = a.opportunity_id
      join public.organizations org on org.id = o.organization_id
     where a.applicant_user_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_accept_volunteer_application(p_application_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v public.volunteer_applications%rowtype;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  select * into v
    from public.volunteer_applications
   where id = p_application_id;
  if v.id is null then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  if not public._canon_volunteer_is_manager(v.opportunity_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v.applicant_user_id = auth.uid() then
    raise exception 'FORBIDDEN';
  end if;
  if v.status = 'SUBMITTED' then
    update public.volunteer_applications
       set status = 'ACCEPTED',
           updated_at = timezone('utc', now())
     where id = v.id
       and status = 'SUBMITTED';
    v.status := 'ACCEPTED';
  end if;
  return jsonb_build_object(
    'id', v.id,
    'opportunity_id', v.opportunity_id,
    'applicant_user_id', v.applicant_user_id,
    'status', v.status,
    'message', v.message
  );
end;
$$;

revoke all on function public.canon_list_volunteer_opportunities(uuid) from public, anon;
revoke all on function public.canon_get_volunteer_opportunity(uuid) from public, anon;
revoke all on function public.canon_apply_volunteer_opportunity(uuid, text) from public, anon;
revoke all on function public.canon_list_volunteer_applicants(uuid) from public, anon;
revoke all on function public.canon_list_my_volunteer_applications() from public, anon;
revoke all on function public.canon_accept_volunteer_application(uuid) from public, anon;

grant execute on function public.canon_list_volunteer_opportunities(uuid) to authenticated;
grant execute on function public.canon_get_volunteer_opportunity(uuid) to authenticated;
grant execute on function public.canon_apply_volunteer_opportunity(uuid, text) to authenticated;
grant execute on function public.canon_list_volunteer_applicants(uuid) to authenticated;
grant execute on function public.canon_list_my_volunteer_applications() to authenticated;
grant execute on function public.canon_accept_volunteer_application(uuid) to authenticated;

notify pgrst, 'reload schema';
