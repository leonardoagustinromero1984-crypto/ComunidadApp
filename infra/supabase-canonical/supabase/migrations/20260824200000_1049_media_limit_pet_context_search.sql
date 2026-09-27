-- 1049: STAGING physical QA
-- 1) public-media 8MiB was HTTP 413 for normal phone video (POST/REEL TUS to Storage).
-- 2) Pets are listed by management context, not globally by auth.uid.
-- 3) Professional search must match veterinary taxonomy, not only display_name.

update storage.buckets
set file_size_limit = 52428800
where id in ('public-media', 'private-media', 'documents', 'moderation-evidence');

alter table public.pets
  add column if not exists management_context_kind text not null default 'PERSON';

alter table public.pets
  add column if not exists management_context_id text;

update public.pets
set management_context_kind = 'PERSON',
    management_context_id = coalesce(management_context_id, created_by_user_id::text)
where management_context_kind is null
   or management_context_kind = 'PERSON';

alter table public.pets
  drop constraint if exists pets_management_context_kind_check;

alter table public.pets
  add constraint pets_management_context_kind_check
  check (management_context_kind in ('PERSON', 'ORGANIZATION', 'PROVIDER'));

drop function if exists public.canon_create_pet(text, text, text, date, integer, integer, integer, date);

create or replace function public.canon_create_pet(
  p_name text,
  p_species text,
  p_birth_precision text default 'UNKNOWN',
  p_birth_date date default null,
  p_birth_year integer default null,
  p_birth_month integer default null,
  p_estimated_age_months integer default null,
  p_estimated_as_of date default null,
  p_management_context_kind text default 'PERSON',
  p_management_context_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_link uuid;
  v_kind text := upper(btrim(coalesce(p_management_context_kind, 'PERSON')));
  v_ctx text := nullif(btrim(coalesce(p_management_context_id, '')), '');
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_kind not in ('PERSON', 'ORGANIZATION', 'PROVIDER') then
    v_kind := 'PERSON';
  end if;
  if v_kind = 'PERSON' then
    v_ctx := coalesce(v_ctx, auth.uid()::text);
  end if;
  insert into public.pets (
    created_by_user_id, name, species_code, birth_precision,
    birth_date, birth_year, birth_month, estimated_age_months, estimated_as_of,
    management_context_kind, management_context_id
  ) values (
    auth.uid(), p_name, p_species, p_birth_precision,
    p_birth_date, p_birth_year, p_birth_month, p_estimated_age_months, p_estimated_as_of,
    v_kind, v_ctx
  ) returning id into v_id;
  insert into public.vitacora_profiles (pet_id) values (v_id);
  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
  ) values (v_id, 'PERSON', auth.uid(), 'OWNER', auth.uid())
  returning id into v_link;
  insert into public.pet_permission_grants (pet_id, link_id, subject_person_id, permission_code, granted_by)
  select v_id, v_link, auth.uid(), code, auth.uid()
  from public.permission_codes where scope in ('PET', 'VITACORA');
  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (v_id, v_link, auth.uid(), 'CREATED_OWNER');
  insert into public.pet_lifecycle_events (pet_id, to_status, actor_user_id)
  values (v_id, 'ACTIVE', auth.uid());
  return v_id;
end;
$$;

create or replace function public.canon_search_vitacora_access_targets(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_q text := btrim(coalesce(p_query, ''));
  v_ql text := lower(btrim(coalesce(p_query, '')));
  v_vet_term boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if char_length(v_q) < 2 then return '[]'::jsonb; end if;
  v_vet_term := v_ql ~ '(vet|veterinar|clinic|cl[ií]nic)';

  return coalesce((
    select jsonb_agg(row_data order by (row_data->>'display_name'))
    from (
      select jsonb_build_object(
        'target_kind', 'ORGANIZATION',
        'target_id', sp.holder_organization_id::text,
        'display_name', sp.display_name,
        'subtitle', coalesce(org.primary_label, 'Entidad veterinaria'),
        'avatar_asset_id', org.logo_asset_id,
        'verified', org.verification_status = 'VERIFIED'
      ) as row_data
      from public.service_providers sp
      join public.organizations org on org.id = sp.holder_organization_id
      where sp.lifecycle_status = 'ACTIVE'
        and sp.holder_kind = 'ORGANIZATION'
        and (
          exists (
            select 1 from public.organization_capabilities oc
            where oc.organization_id = org.id
              and oc.capability in ('VETERINARY_CLINIC', 'PROVIDER', 'NGO', 'SHELTER')
          )
          or org.primary_label ilike 'VETERINARY%'
          or exists (
            select 1 from public.service_offerings o
            where o.provider_id = sp.id
              and o.active
              and o.category_code in ('VETERINARY', 'BOARDING', 'GROOMING')
          )
        )
        and (
          v_vet_term
          or sp.display_name ilike ('%' || v_q || '%')
          or coalesce(org.primary_label, '') ilike ('%' || v_q || '%')
        )
      union all
      select jsonb_build_object(
        'target_kind', 'PERSON',
        'target_id', sp.holder_person_id::text,
        'display_name', sp.display_name,
        'subtitle', 'Profesional independiente',
        'avatar_asset_id', per.avatar_asset_id,
        'verified', false
      ) as row_data
      from public.service_providers sp
      join public.persons per on per.user_id = sp.holder_person_id
      where sp.lifecycle_status = 'ACTIVE'
        and sp.holder_kind = 'PERSON'
        and exists (
          select 1 from public.service_offerings o
          where o.provider_id = sp.id
            and o.active
            and o.category_code in ('VETERINARY', 'BOARDING', 'GROOMING')
        )
        and (
          v_vet_term
          or sp.display_name ilike ('%' || v_q || '%')
          or coalesce(per.display_name, '') ilike ('%' || v_q || '%')
        )
    ) listed
    limit 25
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_create_pet(text, text, text, date, integer, integer, integer, date, text, text) to authenticated;
grant execute on function public.canon_search_vitacora_access_targets(text) to authenticated;
