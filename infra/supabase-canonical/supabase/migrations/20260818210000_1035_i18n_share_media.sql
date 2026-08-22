-- LeoVer Canonical
-- Logical migration: 1035
-- SOCIAL-MEDIA-02 / SHARE-01 / I18N-GEO-01
-- Country markets, message share payload, person/org international fields.
-- Existing location_nodes IDs are preserved. PROVINCE maps to AdministrativeArea(country=AR).

create table if not exists public.country_markets (
  iso_alpha2 text primary key,
  iso_alpha3 text null,
  display_name text not null,
  default_locale text not null,
  default_currency text not null,
  default_timezone text null,
  calling_code text not null,
  location_node_id text null references public.location_nodes(id),
  administrative_area_label text not null default 'Administrative area',
  locality_label text not null default 'Locality',
  enabled boolean not null default false,
  onboarding_enabled boolean not null default false,
  commercial_enabled boolean not null default false,
  created_at timestamptz not null default timezone('utc', now())
);

insert into public.country_markets (
  iso_alpha2, iso_alpha3, display_name, default_locale, default_currency,
  default_timezone, calling_code, location_node_id,
  administrative_area_label, locality_label,
  enabled, onboarding_enabled, commercial_enabled
) values
  ('AR','ARG','Argentina','es-AR','ARS','America/Argentina/Buenos_Aires','54','loc-ar','Provincia','Localidad', true, true, true),
  ('UY','URY','Uruguay','es-UY','UYU','America/Montevideo','598', null, 'Departamento','Localidad', false, false, false),
  ('CL','CHL','Chile','es-CL','CLP','America/Santiago','56', null, 'Región','Comuna', false, false, false),
  ('MX','MEX','México','es-MX','MXN','America/Mexico_City','52', null, 'Estado','Municipio', false, false, false),
  ('ES','ESP','España','es-ES','EUR','Europe/Madrid','34', null, 'Comunidad autónoma','Municipio', false, false, false),
  ('US','USA','United States','en-US','USD','America/New_York','1', null, 'State','City', false, false, false),
  ('BR','BRA','Brasil','pt-BR','BRL','America/Sao_Paulo','55', null, 'Estado','Município', false, false, false)
on conflict (iso_alpha2) do update set
  display_name = excluded.display_name,
  default_locale = excluded.default_locale,
  default_currency = excluded.default_currency,
  location_node_id = coalesce(public.country_markets.location_node_id, excluded.location_node_id),
  enabled = public.country_markets.enabled,
  onboarding_enabled = public.country_markets.onboarding_enabled;

alter table public.messages
  add column if not exists payload jsonb null;

alter table public.persons
  add column if not exists locale text null,
  add column if not exists timezone text null,
  add column if not exists e164_phone text null,
  add column if not exists country_iso text null;

alter table public.organizations
  add column if not exists timezone text null,
  add column if not exists address_line text null,
  add column if not exists country_iso text null;

-- Existing AR geography: provinces already parented to loc-ar (COUNTRY).
update public.persons p
set country_iso = 'AR',
    locale = coalesce(p.locale, 'es-AR'),
    timezone = coalesce(p.timezone, 'America/Argentina/Buenos_Aires')
where p.country_iso is null
  and (
    p.home_locality_id is null
    or exists (
      select 1
      from public.location_nodes loc
      join public.location_nodes admin on admin.id = loc.parent_id
      left join public.location_nodes country on country.id = admin.parent_id
      where loc.id = p.home_locality_id
        and (
          loc.id like 'loc-ar%'
          or admin.id like 'loc-ar%'
          or country.iso_code = 'AR'
          or admin.iso_code like 'AR-%'
        )
    )
  );

update public.organizations o
set country_iso = coalesce(o.country_iso, 'AR'),
    timezone = coalesce(o.timezone, 'America/Argentina/Buenos_Aires')
where o.country_iso is null
  and (
    o.home_locality_id is null
    or o.home_locality_id like 'loc-ar%'
  );

drop function if exists public.canon_send_message(uuid, text);

create or replace function public.canon_send_message(
  p_conversation_id uuid,
  p_body text,
  p_payload jsonb default null
)
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
  insert into public.messages (conversation_id, actor_user_id, body, payload)
  values (p_conversation_id, auth.uid(), btrim(p_body), p_payload)
  returning id into v_id;
  return v_id;
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
      'payload', m.payload,
      'created_at', m.created_at
    ) order by m.created_at)
    from public.messages m
    where m.conversation_id = p_conversation_id and m.hidden_at is null
  ), '[]'::jsonb);
end;
$$;

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
      'created_at', c.created_at,
      'peer_user_id', peer.person_id,
      'peer_name', per.display_name,
      'peer_username', per.username,
      'last_message_text', last.body,
      'last_message_at', last.created_at
    ) order by coalesce(last.created_at, c.created_at) desc)
    from public.conversations c
    left join lateral (
      select p.person_id
      from public.conversation_participants p
      where p.conversation_id = c.id
        and p.left_at is null
        and p.person_id is distinct from auth.uid()
      order by p.joined_at
      limit 1
    ) peer on true
    left join public.persons per on per.user_id = peer.person_id
    left join lateral (
      select m.body, m.created_at
      from public.messages m
      where m.conversation_id = c.id and m.hidden_at is null
      order by m.created_at desc
      limit 1
    ) last on true
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

create or replace function public.canon_list_country_markets()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select coalesce((
    select jsonb_agg(jsonb_build_object(
      'iso_alpha2', m.iso_alpha2,
      'iso_alpha3', m.iso_alpha3,
      'display_name', m.display_name,
      'default_locale', m.default_locale,
      'default_currency', m.default_currency,
      'default_timezone', m.default_timezone,
      'calling_code', m.calling_code,
      'location_node_id', m.location_node_id,
      'administrative_area_label', m.administrative_area_label,
      'locality_label', m.locality_label,
      'enabled', m.enabled,
      'onboarding_enabled', m.onboarding_enabled,
      'commercial_enabled', m.commercial_enabled
    ) order by m.display_name)
    from public.country_markets m
  ), '[]'::jsonb);
$$;

grant select on public.country_markets to anon, authenticated;
grant execute on function public.canon_send_message(uuid, text, jsonb) to authenticated;
grant execute on function public.canon_list_messages(uuid) to authenticated;
grant execute on function public.canon_list_conversations() to authenticated;
grant execute on function public.canon_list_country_markets() to authenticated, anon;
