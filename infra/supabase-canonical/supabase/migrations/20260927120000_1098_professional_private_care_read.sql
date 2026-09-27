-- 1098: Canonical read path for provider-private clinical history.
-- veterinary_care_records stays revoked from anon/authenticated (1020).
-- Writes already go through canon_record_vet_care (1064).
-- Legacy m28_list_pet_cares returns veterinary_professional_cares and is not
-- part of this schema. This RPC is the product read for the same table.
-- A caller sees a row only through an active HEALTH clinic grant on that
-- row's organization, or through their own professional profile plus a
-- personal HEALTH grant. Pet holders do not gain access here.
-- Accepted VitaCora proposals stay on canon_list_vitacora_proposals.

create or replace function public.canon_list_professional_pet_cares(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_prof uuid;
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if p_pet_id is null then
    raise exception 'VALIDATION';
  end if;

  select pp.id into v_prof
    from public.professional_profiles pp
   where pp.person_id = v_uid
     and pp.active
   limit 1;

  if not exists (
    select 1
      from public.vitacora_access_grants g
     where g.pet_id = p_pet_id
       and g.revoked_at is null
       and (g.expires_at is null or g.expires_at > timezone('utc', now()))
       and g.scope in ('HEALTH', 'ESSENTIAL_AND_HEALTH', 'FULL_SHAREABLE')
       and (
         (g.grantee_person_id = v_uid and v_prof is not null)
         or (
           g.grantee_organization_id is not null
           and public._acl_org_member(v_uid, g.grantee_organization_id)
         )
       )
  ) then
    raise exception 'FORBIDDEN';
  end if;

  return coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'id', r.id,
        'pet_id', r.pet_id,
        'organization_id', r.organization_id,
        'professional_profile_id', r.professional_profile_id,
        'provenance', r.provenance,
        'summary', r.summary,
        'care_on', r.care_on,
        'created_at', r.created_at
      )
      order by r.care_on desc, r.created_at desc
    )
    from public.veterinary_care_records r
    where r.pet_id = p_pet_id
      and (
        (
          v_prof is not null
          and r.professional_profile_id = v_prof
          and exists (
            select 1
              from public.vitacora_access_grants g
             where g.pet_id = r.pet_id
               and g.grantee_person_id = v_uid
               and g.revoked_at is null
               and (g.expires_at is null or g.expires_at > timezone('utc', now()))
               and g.scope in ('HEALTH', 'ESSENTIAL_AND_HEALTH', 'FULL_SHAREABLE')
          )
        )
        or (
          public._acl_org_member(v_uid, r.organization_id)
          and exists (
            select 1
              from public.vitacora_access_grants g
             where g.pet_id = r.pet_id
               and g.grantee_organization_id = r.organization_id
               and g.revoked_at is null
               and (g.expires_at is null or g.expires_at > timezone('utc', now()))
               and g.scope in ('HEALTH', 'ESSENTIAL_AND_HEALTH', 'FULL_SHAREABLE')
          )
        )
      )
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_list_professional_pet_cares(uuid) from public, anon;
grant execute on function public.canon_list_professional_pet_cares(uuid) to authenticated;

comment on function public.canon_list_professional_pet_cares(uuid) is
  'Provider-private veterinary_care_records for an active clinic or personal HEALTH grant. No table SELECT grant.';

notify pgrst, 'reload schema';
