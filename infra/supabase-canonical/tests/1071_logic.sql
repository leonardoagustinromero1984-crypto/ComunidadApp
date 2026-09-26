select
  public.holder_xor_ok('PERSON', '00000000-0000-4000-8000-000000000001', null) as person_xor_ok,
  public.holder_xor_ok('ORGANIZATION', null, '00000000-0000-4000-8000-000000000002') as org_xor_ok,
  public.holder_xor_ok('PERSON', null, '00000000-0000-4000-8000-000000000002') as mixed_xor_rejected,
  (select count(*) = 0 from public.pets where current_custodian_kind is null) as all_pets_have_custodian,
  (select count(*) from public.pet_care_stages s
     join public.pets p on p.id = s.pet_id
    where s.closed_at is null) as open_stages,
  (select count(*) from public.pets) as pets,
  exists(
    select 1 from public.organization_role_permissions
    where permission_code = 'org.pets.transfer'
  ) as org_transfer_seeded;
