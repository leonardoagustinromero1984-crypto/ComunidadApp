-- 1106: VitaCora moment list exposes the linked asset and, for a finding photo,
-- the related lost/found case. Display name for an unnamed FOUND_CASE is
-- presentation only; pets.name is not rewritten.

create or replace function public.canon_list_vitacora_moments(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_display_name text;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.view')
    or public._acl_is_admin(auth.uid())
  ) then
    raise exception 'FORBIDDEN';
  end if;
  perform public._canon_assert_vitacora_not_retired(p_pet_id);

  select case
    when p.origin_kind = 'FOUND_CASE'
     and lower(btrim(coalesce(p.name, ''))) in ('', 'sin nombre')
    then 'Encontrado'
    else coalesce(nullif(btrim(p.name), ''), 'Mascota')
  end
    into v_display_name
  from public.pets p
  where p.id = p_pet_id;

  return coalesce((
    select jsonb_agg(row_data order by (row_data->>'created_at') desc)
    from (
      select jsonb_build_object(
        'id', m.id,
        'kind', m.kind,
        'title', m.title,
        'body', case
          when a.id is not null
           and public._canon_personal_media_withheld_from(auth.uid(), a)
          then null
          else m.body
        end,
        'asset_id', case
          when a.id is not null
           and public._canon_personal_media_withheld_from(auth.uid(), a)
          then null
          else a.id
        end,
        'lost_found_case_id', case
          when m.kind = 'PHOTO' and m.title = 'Foto del hallazgo' then (
            select al.id
              from public.lost_found_alerts al
             where al.pet_id = m.pet_id
             order by al.created_at desc
             limit 1
          )
          else null
        end,
        'created_by', m.created_by,
        'created_at', m.created_at
      ) as row_data
      from public.vitacora_moments m
      left join public.media_assets a
        on a.id = public._canon_moment_linked_asset_id(m)
      where m.pet_id = p_pet_id
        and m.hidden_at is null

      union all

      select jsonb_build_object(
        'id', p.id,
        'kind', 'CARE_CREATED',
        'title', 'Se creó la VitaCora de ' || coalesce(v_display_name, 'Mascota') || '.',
        'body', null,
        'created_by', p.created_by_user_id,
        'created_at', p.created_at
      )
      from public.pets p
      where p.id = p_pet_id

      union all

      select jsonb_build_object(
        'id', t.id,
        'kind', 'CARE_TRANSFER',
        'title',
          coalesce(v_display_name, 'Mascota') || ' pasó a estar bajo el cuidado de ' ||
          coalesce(
            public._canon_actor_display_name(
              t.target_kind, t.target_person_id, t.target_organization_id
            ),
            'un nuevo cuidador'
          ) || '.',
        'body', null,
        'created_by', t.decided_by_user_id,
        'created_at', t.decided_at
      )
      from public.pet_care_transfers t
      where t.pet_id = p_pet_id
        and t.status = 'ACCEPTED'
        and t.decided_at is not null
    ) listed
  ), '[]'::jsonb);
end;
$$;
