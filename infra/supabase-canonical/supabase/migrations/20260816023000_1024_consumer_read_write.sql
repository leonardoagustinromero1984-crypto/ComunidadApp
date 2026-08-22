-- LeoVer Canonical Baseline
-- Logical migration: 1024
-- Consumer read/write enablement. Forward-only. Does not edit 1000–1023.
-- Technical gaps only: Health read, holder end, list/create RPCs for existing V1 tables.

-- ---------------------------------------------------------------------------
-- Minimal columns so existing V1 UI can persist non-PII description text.
-- Precise coordinates stay protected. No contact/PII columns.
-- ---------------------------------------------------------------------------

alter table public.lost_found_alerts
  add column if not exists note text null,
  add column if not exists species_code text null references public.species(code);

alter table public.adoption_publications
  add column if not exists note text null;

create index if not exists lost_found_alerts_status_kind_idx
  on public.lost_found_alerts (status, kind, created_at desc);

create index if not exists adoption_publications_status_idx
  on public.adoption_publications (status, created_at desc);

create index if not exists social_posts_visible_idx
  on public.social_posts (created_at desc)
  where hidden_at is null;

-- ---------------------------------------------------------------------------
-- Health read
-- ---------------------------------------------------------------------------

create or replace function public.canon_get_pet_health(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public._acl_pet_holder(auth.uid(), p_pet_id)
    or public._acl_is_admin(auth.uid())
  ) then
    raise exception 'FORBIDDEN';
  end if;

  return jsonb_build_object(
    'pet_id', p_pet_id,
    'allergies', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', a.id, 'name', a.name, 'source', a.source, 'status', a.status,
        'created_at', a.created_at
      ) order by a.created_at desc)
      from public.pet_allergies a
      where a.pet_id = p_pet_id and a.status = 'ACTIVE'
    ), '[]'::jsonb),
    'medications', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', m.id, 'name', m.name, 'instructions', m.instructions,
        'source', m.source, 'status', m.status, 'created_at', m.created_at
      ) order by m.created_at desc)
      from public.pet_medications m
      where m.pet_id = p_pet_id and m.status = 'ACTIVE'
    ), '[]'::jsonb),
    'vaccinations', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', v.id, 'vaccine_name', v.vaccine_name,
        'administered_on', v.administered_on, 'source', v.source,
        'created_at', v.created_at
      ) order by v.created_at desc)
      from public.pet_declared_vaccinations v
      where v.pet_id = p_pet_id
    ), '[]'::jsonb),
    'parasite_treatments', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', t.id, 'kind', t.kind, 'product_name', t.product_name,
        'treated_on', t.treated_on, 'source', t.source, 'created_at', t.created_at
      ) order by t.treated_on desc, t.created_at desc)
      from public.pet_parasite_treatments t
      where t.pet_id = p_pet_id
    ), '[]'::jsonb),
    'conditions', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', c.id, 'name', c.name, 'source', c.source, 'status', c.status,
        'created_at', c.created_at
      ) order by c.created_at desc)
      from public.pet_conditions c
      where c.pet_id = p_pet_id and c.status = 'ACTIVE'
    ), '[]'::jsonb),
    'weights', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', w.id, 'kilograms', w.kilograms, 'measured_on', w.measured_on,
        'source', w.source, 'created_at', w.created_at
      ) order by w.measured_on desc, w.created_at desc)
      from public.pet_weights w
      where w.pet_id = p_pet_id
    ), '[]'::jsonb),
    'care_instructions', (
      select jsonb_build_object(
        'feeding', i.feeding, 'medication', i.medication,
        'specials', i.specials, 'updated_at', i.updated_at
      )
      from public.pet_care_instructions i
      where i.pet_id = p_pet_id
    ),
    'declared_notes', (
      select h.notes from public.pet_declared_health h where h.pet_id = p_pet_id
    )
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Holder end (UI already exposes revoke). Cannot end the last ACTIVE OWNER.
-- ---------------------------------------------------------------------------

create or replace function public.canon_end_pet_responsibility(p_link_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link public.pet_responsibility_links%rowtype;
  v_owners integer;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_link from public.pet_responsibility_links where id = p_link_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_link.status <> 'ACTIVE' then return p_link_id; end if;
  if not public._acl_pet_permission(auth.uid(), v_link.pet_id, 'responsibility.manage') then
    raise exception 'FORBIDDEN';
  end if;
  if v_link.role = 'OWNER' then
    select count(*) into v_owners
    from public.pet_responsibility_links
    where pet_id = v_link.pet_id and status = 'ACTIVE' and role = 'OWNER';
    if v_owners <= 1 then
      raise exception 'LAST_OWNER_REQUIRED';
    end if;
  end if;
  update public.pet_responsibility_links
    set status = 'ENDED', valid_until = timezone('utc', now())
    where id = p_link_id;
  update public.pet_permission_grants
    set revoked_at = timezone('utc', now())
    where link_id = p_link_id and revoked_at is null;
  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (v_link.pet_id, p_link_id, auth.uid(), 'ENDED');
  return p_link_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Person self-update (display + locality only). Username stays canonical RPC.
-- ---------------------------------------------------------------------------

create or replace function public.canon_update_my_person(
  p_display_name text default null,
  p_home_locality_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  update public.persons
    set display_name = coalesce(nullif(btrim(p_display_name), ''), display_name),
        home_locality_id = coalesce(p_home_locality_id, home_locality_id)
    where user_id = auth.uid();
  if not found then raise exception 'NOT_FOUND'; end if;
  return auth.uid();
end;
$$;

create or replace function public.canon_get_public_person(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare v_row public.persons%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row from public.persons where user_id = p_user_id;
  if not found then return null; end if;
  if v_row.user_id <> auth.uid()
     and v_row.privacy_state <> 'PUBLIC_LIMITED'
     and not public._acl_is_admin(auth.uid())
  then
    return null;
  end if;
  return jsonb_build_object(
    'id', v_row.user_id,
    'username', v_row.username,
    'display_name', v_row.display_name,
    'home_locality_id', v_row.home_locality_id,
    'privacy_state', v_row.privacy_state
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- VitaCora moments list
-- ---------------------------------------------------------------------------

create or replace function public.canon_list_vitacora_moments(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.view')
    or public._acl_is_admin(auth.uid())
  ) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', m.id, 'kind', m.kind, 'title', m.title, 'body', m.body,
      'created_by', m.created_by, 'created_at', m.created_at
    ) order by m.created_at desc)
    from public.vitacora_moments m
    where m.pet_id = p_pet_id
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Lost / Found
-- ---------------------------------------------------------------------------

create or replace function public.canon_create_lost_found(
  p_kind text,
  p_pet_id uuid default null,
  p_locality_id text default null,
  p_species text default null,
  p_note text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_kind not in ('LOST', 'FOUND') then raise exception 'KIND_INVALID'; end if;
  if p_pet_id is not null and not public._acl_pet_holder(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.lost_found_alerts (
    kind, pet_id, created_by, locality_id, species_code, note
  ) values (
    p_kind, p_pet_id, auth.uid(), p_locality_id, p_species, p_note
  ) returning id into v_id;
  perform public.canon_audit('lost_found.create', 'lost_found_alerts', v_id, jsonb_build_object('kind', p_kind));
  return v_id;
end;
$$;

create or replace function public.canon_list_lost_found(p_kind text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', a.id,
      'kind', a.kind,
      'status', a.status,
      'public_code', a.public_code,
      'pet_id', a.pet_id,
      'pet_name', p.name,
      'species', coalesce(a.species_code, p.species_code),
      'locality_id', a.locality_id,
      'note', a.note,
      'created_by', a.created_by,
      'created_at', a.created_at
    ) order by a.created_at desc)
    from public.lost_found_alerts a
    left join public.pets p on p.id = a.pet_id
    where a.status = 'OPEN'
      and (p_kind is null or a.kind = p_kind)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_resolve_lost_found(p_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_row public.lost_found_alerts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row from public.lost_found_alerts where id = p_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_row.created_by <> auth.uid()
     and not public._acl_is_admin(auth.uid())
     and (v_row.pet_id is null or not public._acl_pet_holder(auth.uid(), v_row.pet_id))
  then
    raise exception 'FORBIDDEN';
  end if;
  update public.lost_found_alerts
    set status = 'RESOLVED', resolved_at = timezone('utc', now())
    where id = p_id;
  return p_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Adoption
-- ---------------------------------------------------------------------------

create or replace function public.canon_create_adoption(
  p_pet_id uuid,
  p_organization_id uuid default null,
  p_note text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_edit_pet(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  if exists (
    select 1 from public.adoption_publications
    where pet_id = p_pet_id and status = 'OPEN'
  ) then
    raise exception 'ADOPTION_ALREADY_EXISTS';
  end if;
  insert into public.adoption_publications (pet_id, published_by, organization_id, note)
  values (p_pet_id, auth.uid(), p_organization_id, p_note)
  returning id into v_id;
  perform public.canon_audit('adoption.create', 'adoption_publications', v_id, '{}'::jsonb);
  return v_id;
end;
$$;

create or replace function public.canon_list_adoptions()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', a.id,
      'pet_id', a.pet_id,
      'public_code', a.public_code,
      'status', a.status,
      'note', a.note,
      'published_by', a.published_by,
      'organization_id', a.organization_id,
      'name', p.name,
      'species', p.species_code,
      'sex', p.sex,
      'size', p.size,
      'locality_id', p.home_locality_id,
      'created_at', a.created_at
    ) order by a.created_at desc)
    from public.adoption_publications a
    join public.pets p on p.id = a.pet_id
    where a.status = 'OPEN' and p.lifecycle_status = 'ACTIVE'
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_close_adoption(p_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_row public.adoption_publications%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row from public.adoption_publications where id = p_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_row.published_by <> auth.uid()
     and not public._acl_can_edit_pet(auth.uid(), v_row.pet_id)
  then
    raise exception 'FORBIDDEN';
  end if;
  update public.adoption_publications set status = 'CLOSED' where id = p_id;
  return p_id;
end;
$$;

create or replace function public.canon_set_adoption_status(p_id uuid, p_status text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_row public.adoption_publications%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_status not in ('OPEN', 'CLOSED', 'HIDDEN') then raise exception 'STATUS_INVALID'; end if;
  select * into v_row from public.adoption_publications where id = p_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_row.published_by <> auth.uid()
     and not public._acl_can_edit_pet(auth.uid(), v_row.pet_id)
  then
    raise exception 'FORBIDDEN';
  end if;
  update public.adoption_publications set status = p_status where id = p_id;
  return p_id;
end;
$$;

create or replace function public.canon_apply_adoption(p_publication_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
declare v_row public.adoption_publications%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row from public.adoption_publications where id = p_publication_id;
  if not found or v_row.status <> 'OPEN' then raise exception 'NOT_FOUND'; end if;
  insert into public.adoption_applications (publication_id, applicant_user_id)
  values (p_publication_id, auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Organizations
-- ---------------------------------------------------------------------------

create or replace function public.canon_list_my_organizations()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', o.id,
      'name', o.name,
      'slug', o.slug,
      'primary_label', o.primary_label,
      'home_locality_id', o.home_locality_id,
      'lifecycle_status', o.lifecycle_status,
      'membership_status', m.status
    ) order by o.name)
    from public.organization_memberships m
    join public.organizations o on o.id = m.organization_id
    where m.person_id = auth.uid()
      and m.status = 'ACTIVE'
      and o.lifecycle_status = 'ACTIVE'
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Social
-- ---------------------------------------------------------------------------

create or replace function public.canon_create_social_post(
  p_body text,
  p_visibility text default 'PUBLIC'
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_visibility not in ('PUBLIC', 'FOLLOWERS', 'PRIVATE') then
    raise exception 'VISIBILITY_INVALID';
  end if;
  if public.person_is_under_13((select birth_date from public.persons where user_id = auth.uid())) then
    raise exception 'UNDER_13_DENIED';
  end if;
  insert into public.social_posts (author_user_id, body, visibility)
  values (auth.uid(), p_body, p_visibility)
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_list_social_feed()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', s.id,
      'author_user_id', s.author_user_id,
      'author_name', per.display_name,
      'body', s.body,
      'visibility', s.visibility,
      'sponsored', s.sponsored,
      'like_count', (select count(*) from public.social_reactions r where r.post_id = s.id),
      'comment_count', (select count(*) from public.social_comments c where c.post_id = s.id and c.hidden_at is null),
      'created_at', s.created_at
    ) order by s.created_at desc)
    from public.social_posts s
    join public.persons per on per.user_id = s.author_user_id
    where s.hidden_at is null
      and s.visibility = 'PUBLIC'
      and s.sponsored = false
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_react_social_post(p_post_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare v_exists boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select exists (
    select 1 from public.social_reactions
    where post_id = p_post_id and user_id = auth.uid()
  ) into v_exists;
  if v_exists then
    delete from public.social_reactions where post_id = p_post_id and user_id = auth.uid();
    return false;
  end if;
  insert into public.social_reactions (post_id, user_id) values (p_post_id, auth.uid());
  return true;
end;
$$;

create or replace function public.canon_comment_social_post(p_post_id uuid, p_body text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  insert into public.social_comments (post_id, author_user_id, body)
  values (p_post_id, auth.uid(), btrim(p_body))
  returning id into v_id;
  return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Messaging
-- ---------------------------------------------------------------------------

create or replace function public.canon_list_conversations()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id,
      'subject_kind', c.subject_kind,
      'created_at', c.created_at
    ) order by c.created_at desc)
    from public.conversations c
    where exists (
      select 1 from public.conversation_participants p
      where p.conversation_id = c.id
        and p.left_at is null
        and p.person_id = auth.uid()
    )
    and c.archived_at is null
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_messages(p_conversation_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (
    select 1 from public.conversation_participants p
    where p.conversation_id = p_conversation_id
      and p.left_at is null
      and p.person_id = auth.uid()
  ) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', m.id,
      'actor_user_id', m.actor_user_id,
      'body', m.body,
      'created_at', m.created_at
    ) order by m.created_at)
    from public.messages m
    where m.conversation_id = p_conversation_id and m.hidden_at is null
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_send_message(p_conversation_id uuid, p_body text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (
    select 1 from public.conversation_participants p
    where p.conversation_id = p_conversation_id
      and p.left_at is null
      and p.person_id = auth.uid()
  ) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.messages (conversation_id, actor_user_id, body)
  values (p_conversation_id, auth.uid(), btrim(p_body))
  returning id into v_id;
  return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Providers / bookings / events / donations / foster (list + existing writes)
-- ---------------------------------------------------------------------------

create or replace function public.canon_list_providers()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', p.id,
      'holder_kind', p.holder_kind,
      'display_name', p.display_name,
      'categories', coalesce((
        select jsonb_agg(o.category_code)
        from public.service_offerings o
        where o.provider_id = p.id and o.active
      ), '[]'::jsonb)
    ) order by p.display_name)
    from public.service_providers p
    where p.lifecycle_status = 'ACTIVE'
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_my_bookings()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', b.id,
      'pet_id', b.pet_id,
      'provider_id', b.provider_id,
      'status', b.status,
      'starts_at', b.starts_at,
      'zone_id', b.zone_id
    ) order by b.starts_at desc)
    from public.bookings b
    where b.booked_by = auth.uid()
       or public._acl_pet_holder(auth.uid(), b.pet_id)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_events()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', e.id,
      'title', e.title,
      'starts_at', e.starts_at,
      'locality_id', e.locality_id,
      'organization_id', e.organization_id
    ) order by e.starts_at)
    from public.community_events e
    where e.hidden_at is null
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_create_event(
  p_title text,
  p_starts_at timestamptz,
  p_locality_id text default null,
  p_organization_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  insert into public.community_events (
    organization_id, created_by, title, starts_at, zone_id, locality_id
  ) values (
    p_organization_id, auth.uid(), btrim(p_title), p_starts_at,
    'America/Argentina/Buenos_Aires', p_locality_id
  ) returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_list_donation_campaigns()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', d.id,
      'title', d.title,
      'alias_cbu', d.alias_cbu,
      'status', d.status,
      'organization_id', d.organization_id
    ) order by d.created_at desc)
    from public.donation_campaigns d
    where d.status = 'OPEN'
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_create_donation_campaign(
  p_title text,
  p_alias_cbu text default null,
  p_organization_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  insert into public.donation_campaigns (organization_id, created_by, title, alias_cbu)
  values (p_organization_id, auth.uid(), btrim(p_title), p_alias_cbu)
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_upsert_foster_profile(
  p_capacity integer default 1,
  p_locality_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  insert into public.foster_profiles (user_id, capacity, locality_id)
  values (auth.uid(), greatest(p_capacity, 0), p_locality_id)
  on conflict (user_id) do update
    set capacity = excluded.capacity,
        locality_id = excluded.locality_id,
        active = true;
  return auth.uid();
end;
$$;

create or replace function public.canon_list_foster_placements()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', f.id,
      'pet_id', f.pet_id,
      'foster_user_id', f.foster_user_id,
      'status', f.status,
      'starts_at', f.starts_at
    ) order by f.starts_at desc)
    from public.foster_placements f
    where f.foster_user_id = auth.uid()
       or public._acl_pet_holder(auth.uid(), f.pet_id)
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

do $$
declare r record;
begin
  for r in
    select p.proname, pg_get_function_identity_arguments(p.oid) as args
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'canon_get_pet_health',
        'canon_end_pet_responsibility',
        'canon_update_my_person',
        'canon_get_public_person',
        'canon_list_vitacora_moments',
        'canon_create_lost_found',
        'canon_list_lost_found',
        'canon_resolve_lost_found',
        'canon_create_adoption',
        'canon_list_adoptions',
        'canon_close_adoption',
        'canon_set_adoption_status',
        'canon_apply_adoption',
        'canon_list_my_organizations',
        'canon_create_social_post',
        'canon_list_social_feed',
        'canon_react_social_post',
        'canon_comment_social_post',
        'canon_list_conversations',
        'canon_list_messages',
        'canon_send_message',
        'canon_list_providers',
        'canon_list_my_bookings',
        'canon_list_events',
        'canon_create_event',
        'canon_list_donation_campaigns',
        'canon_create_donation_campaign',
        'canon_upsert_foster_profile',
        'canon_list_foster_placements'
      )
  loop
    execute format('revoke all on function public.%I(%s) from public, anon', r.proname, r.args);
    execute format('grant execute on function public.%I(%s) to authenticated', r.proname, r.args);
  end loop;
end$$;
