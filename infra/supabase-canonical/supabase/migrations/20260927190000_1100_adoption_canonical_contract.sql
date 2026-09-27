-- LeoVer Canonical Baseline
-- Logical migration: 1100
-- Adoption application read contract, apply idempotency, and publication closure.
-- Does not edit 1000–1099. Does not grant table SELECT. Client execute is authenticated only.
--
-- Status model already in force (not extended):
--   adoption_publications.status: OPEN | CLOSED | HIDDEN
--     CLOSED is the existing terminal status (canon_close_adoption / canon_set_adoption_status).
--   adoption_applications.status: PENDING | SUBMITTED | IN_REVIEW | ACCEPTED | PAUSED
--     | REJECTED | WITHDRAWN | COMPLETED | CLOSED
--   pet_care_transfers.status: PENDING | ACCEPTED | REJECTED | CANCELLED
--
-- Active / non-terminal applications (at most one per publication + applicant):
--   PENDING, SUBMITTED, IN_REVIEW, ACCEPTED, PAUSED
-- Historical terminal applications stay repeatable:
--   REJECTED, WITHDRAWN, COMPLETED, CLOSED
--
-- Publication leaves OPEN only inside _canon_complete_adoptions_for_pet,
-- which runs from the existing AFTER UPDATE trigger when a care transfer
-- becomes ACCEPTED. Accepting an application does not close the publication,
-- change the pet, change VitaCora, or change the responsible holder.
-- Interviews, documents, and agreements are not completion gates.

-- ---------------------------------------------------------------------------
-- One active application per publication + applicant. History is kept.
-- ---------------------------------------------------------------------------

do $$
begin
  if exists (
    select 1
    from public.adoption_applications
    where status in ('PENDING', 'SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'PAUSED')
    group by publication_id, applicant_user_id
    having count(*) > 1
  ) then
    raise exception 'ADOPTION_ACTIVE_APPLICATION_DUPLICATES'
      using hint = 'Resolve duplicate active applications before the unique index. Do not delete CLOSED or COMPLETED history.';
  end if;
end
$$;

create unique index if not exists adoption_applications_one_active_uidx
  on public.adoption_applications (publication_id, applicant_user_id)
  where status in ('PENDING', 'SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'PAUSED');

-- ---------------------------------------------------------------------------
-- Apply: return the current active application instead of inserting another.
-- ---------------------------------------------------------------------------

create or replace function public.canon_apply_adoption(p_publication_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_row public.adoption_publications%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row
    from public.adoption_publications
   where id = p_publication_id
   for update;
  if not found or v_row.status <> 'OPEN' then
    raise exception 'NOT_FOUND';
  end if;

  select a.id into v_id
    from public.adoption_applications a
   where a.publication_id = p_publication_id
     and a.applicant_user_id = auth.uid()
     and a.status in ('PENDING', 'SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'PAUSED')
   order by a.created_at desc
   limit 1;
  if v_id is not null then
    return v_id;
  end if;

  begin
    insert into public.adoption_applications (publication_id, applicant_user_id)
    values (p_publication_id, auth.uid())
    returning id into v_id;
  exception
    when unique_violation then
      select a.id into v_id
        from public.adoption_applications a
       where a.publication_id = p_publication_id
         and a.applicant_user_id = auth.uid()
         and a.status in ('PENDING', 'SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'PAUSED')
       order by a.created_at desc
       limit 1;
      if v_id is null then
        raise;
      end if;
  end;
  return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Authorization helper. Same manager rule as canon_accept_adoption_application:
-- publisher or current pet holder. Not granted to clients.
-- ---------------------------------------------------------------------------

create or replace function public._canon_can_manage_adoption_publication(
  p_user_id uuid,
  p_publication_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_user_id is not null and exists (
    select 1
      from public.adoption_publications pub
     where pub.id = p_publication_id
       and (
         pub.published_by = p_user_id
         or public._acl_pet_holder(p_user_id, pub.pet_id)
       )
  );
$$;

-- Minimal application projection. No email, phone, address, or profile notes.
create or replace function public._canon_adoption_application_json(p_application_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', a.id,
    'publication_id', a.publication_id,
    'applicant_user_id', a.applicant_user_id,
    'applicant_name', coalesce(nullif(btrim(person.display_name), ''), person.username),
    'status', a.status,
    'created_at', a.created_at,
    'pet_id', pub.pet_id,
    'pet_name', pet.name,
    'publication_status', pub.status
  )
  from public.adoption_applications a
  join public.adoption_publications pub on pub.id = a.publication_id
  join public.pets pet on pet.id = pub.pet_id
  join public.persons person on person.user_id = a.applicant_user_id
  where a.id = p_application_id;
$$;

create or replace function public.canon_list_my_adoption_applications()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(
      public._canon_adoption_application_json(a.id)
      order by a.created_at desc
    )
    from public.adoption_applications a
    where a.applicant_user_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_adoption_applications(p_publication_id uuid default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_pub public.adoption_publications%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_publication_id is not null then
    select * into v_pub from public.adoption_publications where id = p_publication_id;
    if not found then raise exception 'NOT_FOUND'; end if;
    if not public._canon_can_manage_adoption_publication(auth.uid(), p_publication_id) then
      raise exception 'FORBIDDEN';
    end if;
  end if;
  return coalesce((
    select jsonb_agg(
      public._canon_adoption_application_json(a.id)
      order by a.created_at desc
    )
    from public.adoption_applications a
    join public.adoption_publications pub on pub.id = a.publication_id
    where (
      p_publication_id is not null
      and a.publication_id = p_publication_id
    ) or (
      p_publication_id is null
      and public._canon_can_manage_adoption_publication(auth.uid(), pub.id)
    )
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_adoption_application(p_application_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_app public.adoption_applications%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_application_id is null then raise exception 'NOT_FOUND'; end if;
  select * into v_app from public.adoption_applications where id = p_application_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if auth.uid() is distinct from v_app.applicant_user_id
     and not public._canon_can_manage_adoption_publication(auth.uid(), v_app.publication_id) then
    raise exception 'FORBIDDEN';
  end if;
  return public._canon_adoption_application_json(v_app.id);
end;
$$;

-- ---------------------------------------------------------------------------
-- Care-transfer acceptance is the only publication terminal step.
-- Selected application -> COMPLETED. Other still-active applications -> CLOSED.
-- OPEN publication -> CLOSED. Hidden/closed publications are left as they are.
-- ---------------------------------------------------------------------------

create or replace function public._canon_complete_adoptions_for_pet(p_pet_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.adoption_applications a
     set status = 'COMPLETED'
    from public.adoption_publications p
   where p.id = a.publication_id
     and p.pet_id = p_pet_id
     and a.status = 'ACCEPTED';
  update public.adoption_applications a
     set status = 'CLOSED'
    from public.adoption_publications p
   where p.id = a.publication_id
     and p.pet_id = p_pet_id
     and a.status in ('PAUSED', 'PENDING', 'SUBMITTED', 'IN_REVIEW');
  update public.adoption_publications
     set status = 'CLOSED'
   where pet_id = p_pet_id
     and status = 'OPEN';
end;
$$;

-- ---------------------------------------------------------------------------
-- Privileges. Direct table SELECT stays revoked.
-- ---------------------------------------------------------------------------

revoke all on table public.adoption_applications from public, anon, authenticated;
revoke all on table public.adoption_publications from public, anon, authenticated;

revoke all on function public._canon_can_manage_adoption_publication(uuid, uuid) from public, anon, authenticated;
revoke all on function public._canon_adoption_application_json(uuid) from public, anon, authenticated;
revoke all on function public._canon_complete_adoptions_for_pet(uuid) from public, anon, authenticated;

revoke all on function public.canon_apply_adoption(uuid) from public, anon;
revoke all on function public.canon_list_my_adoption_applications() from public, anon;
revoke all on function public.canon_list_adoption_applications(uuid) from public, anon;
revoke all on function public.canon_get_adoption_application(uuid) from public, anon;

grant execute on function public.canon_apply_adoption(uuid) to authenticated;
grant execute on function public.canon_list_my_adoption_applications() to authenticated;
grant execute on function public.canon_list_adoption_applications(uuid) to authenticated;
grant execute on function public.canon_get_adoption_application(uuid) to authenticated;
