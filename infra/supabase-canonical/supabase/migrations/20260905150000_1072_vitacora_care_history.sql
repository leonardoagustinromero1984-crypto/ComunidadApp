-- 1072: expose accepted care transfers in VitaCora history.
-- Does not edit 1071. Does not change multimedia share policy.
-- Reuses pet_care_transfers ACCEPTED rows; does not insert a second moment.

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
    select jsonb_agg(row_data order by (row_data->>'created_at') desc)
    from (
      select jsonb_build_object(
        'id', m.id,
        'kind', m.kind,
        'title', m.title,
        'body', m.body,
        'created_by', m.created_by,
        'created_at', m.created_at
      ) as row_data
      from public.vitacora_moments m
      where m.pet_id = p_pet_id
        and m.hidden_at is null

      union all

      select jsonb_build_object(
        'id', p.id,
        'kind', 'CARE_CREATED',
        'title', 'Se creó la VitaCora de ' || p.name || '.',
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
          p.name || ' pasó a estar bajo el cuidado de ' ||
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
      join public.pets p on p.id = t.pet_id
      where t.pet_id = p_pet_id
        and t.status = 'ACCEPTED'
        and t.decided_at is not null
    ) listed
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_list_vitacora_moments(uuid) from public, anon;
grant execute on function public.canon_list_vitacora_moments(uuid) to authenticated;

notify pgrst, 'reload schema';
