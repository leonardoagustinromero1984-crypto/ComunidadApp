-- QA-01 domain seed. Requires qa_* PERSON rows (created by qa01.ps1 Admin signup).
-- Idempotent upserts by username / slug / QA • name.

do $$
declare
  u_public uuid;
  u_private uuid;
  u_rescuer uuid;
  u_org_admin uuid;
  u_vet uuid;
  u_provider uuid;
  u_daycare uuid;
  u_second uuid;
  org_shelter uuid;
  org_vet uuid;
  org_day uuid;
  role_shelter uuid;
  role_vet uuid;
  role_day uuid;
  pet_a uuid; pet_b uuid; pet_c uuid; pet_d uuid; pet_e uuid; pet_f uuid; pet_g uuid; pet_h uuid;
  prov_caba uuid; prov_ba uuid; prov_cba uuid; prov_sf uuid; prov_ab uuid; prov_walk uuid;
  loc_caba text := 'loc-ar-loc-caba';
  loc_palermo text := 'loc-ar-loc-palermo';
  loc_sv text := 'loc-ar-loc-san-vicente';
  loc_ab text := 'loc-ar-loc-almirante-brown';
  loc_cba text := 'loc-ar-loc-cordoba-cap';
  loc_sf text := 'loc-ar-loc-santa-fe';
  conv uuid;
begin
  if not exists (select 1 from public.location_nodes where id = loc_ab) then
    loc_ab := loc_sv;
  end if;
  if not exists (select 1 from public.location_nodes where id = loc_cba) then
    select id into loc_cba from public.location_nodes
    where parent_id = 'loc-ar-prov-cordoba' and kind = 'LOCALITY' limit 1;
  end if;
  if not exists (select 1 from public.location_nodes where id = loc_sf) then
    select id into loc_sf from public.location_nodes
    where parent_id = 'loc-ar-prov-santa-fe' and kind = 'LOCALITY' limit 1;
  end if;

  select user_id into u_public from public.persons where username = 'qa_public';
  select user_id into u_private from public.persons where username = 'qa_private';
  select user_id into u_rescuer from public.persons where username = 'qa_rescuer';
  select user_id into u_org_admin from public.persons where username = 'qa_org_admin';
  select user_id into u_vet from public.persons where username = 'qa_vet';
  select user_id into u_provider from public.persons where username = 'qa_provider';
  select user_id into u_daycare from public.persons where username = 'qa_daycare';
  select user_id into u_second from public.persons where username = 'qa_second';

  if u_public is null or u_private is null or u_rescuer is null or u_org_admin is null
     or u_vet is null or u_provider is null or u_daycare is null or u_second is null then
    raise exception 'QA_01_PERSONS_MISSING: run qa01.ps1 seed after LEOVER_QA_PASSWORD is set';
  end if;

  update public.persons set
    display_name = 'QA • Persona Pública',
    privacy_state = 'PUBLIC_LIMITED',
    home_locality_id = loc_palermo
  where user_id = u_public;
  update public.persons set
    display_name = 'QA • Persona Privada',
    privacy_state = 'PRIVATE',
    home_locality_id = loc_sv
  where user_id = u_private;
  update public.persons set
    display_name = 'QA • Rescatista',
    privacy_state = 'PUBLIC_LIMITED',
    home_locality_id = loc_ab
  where user_id = u_rescuer;
  update public.persons set
    display_name = 'QA • Admin Refugio',
    privacy_state = 'PUBLIC_LIMITED',
    home_locality_id = loc_sv
  where user_id = u_org_admin;
  update public.persons set
    display_name = 'QA • Veterinaria Persona',
    privacy_state = 'PUBLIC_LIMITED',
    home_locality_id = loc_caba
  where user_id = u_vet;
  update public.persons set
    display_name = 'QA • Paseos',
    privacy_state = 'PUBLIC_LIMITED',
    home_locality_id = loc_palermo
  where user_id = u_provider;
  update public.persons set
    display_name = 'QA • Guardería Persona',
    privacy_state = 'PUBLIC_LIMITED',
    home_locality_id = loc_cba
  where user_id = u_daycare;
  update public.persons set
    display_name = 'QA • Segundo Consumidor',
    privacy_state = 'PUBLIC_LIMITED',
    home_locality_id = loc_sf
  where user_id = u_second;

  insert into public.friendships (requester_id, addressee_id, status, responded_at)
  values (u_second, u_private, 'ACCEPTED', timezone('utc', now()))
  on conflict (requester_id, addressee_id) do update set status = 'ACCEPTED';
  insert into public.friendships (requester_id, addressee_id, status)
  values (u_rescuer, u_private, 'PENDING')
  on conflict (requester_id, addressee_id) do update set status = 'PENDING';

  insert into public.organizations (name, slug, primary_label, home_locality_id, created_by_user_id)
  select * from (values
    ('QA • Refugio San Vicente', 'qa-refugio', 'SHELTER', loc_sv, u_org_admin),
    ('QA • Veterinaria Palermo', 'qa-vet-org', 'VETERINARY_CLINIC', loc_palermo, u_vet),
    ('QA • Guardería Córdoba', 'qa-guarderia', 'DAYCARE', loc_cba, u_daycare)
  ) as v(name, slug, primary_label, home_locality_id, created_by_user_id)
  where not exists (select 1 from public.organizations o where o.slug = v.slug);

  select id into org_shelter from public.organizations where slug = 'qa-refugio';
  select id into org_vet from public.organizations where slug = 'qa-vet-org';
  select id into org_day from public.organizations where slug = 'qa-guarderia';

  insert into public.organization_capabilities (organization_id, capability) values
    (org_shelter, 'SHELTER'), (org_vet, 'VETERINARY_CLINIC'), (org_day, 'DAYCARE')
  on conflict do nothing;

  insert into public.organization_public_profiles (organization_id, bio, published) values
    (org_shelter, 'Refugio ficticio QA.', true),
    (org_vet, 'Clínica ficticia QA.', true),
    (org_day, 'Guardería ficticia QA.', true)
  on conflict (organization_id) do update set published = true;

  insert into public.organization_roles (organization_id, code, name, is_system) values
    (org_shelter, 'ADMIN', 'Admin', true),
    (org_vet, 'ADMIN', 'Admin', true),
    (org_day, 'ADMIN', 'Admin', true)
  on conflict (organization_id, code) do nothing;
  select id into role_shelter from public.organization_roles where organization_id = org_shelter and code = 'ADMIN';
  select id into role_vet from public.organization_roles where organization_id = org_vet and code = 'ADMIN';
  select id into role_day from public.organization_roles where organization_id = org_day and code = 'ADMIN';

  insert into public.organization_memberships (organization_id, person_id, role_id, status)
  select * from (values
    (org_shelter, u_org_admin, role_shelter, 'ACTIVE'),
    (org_vet, u_vet, role_vet, 'ACTIVE'),
    (org_day, u_daycare, role_day, 'ACTIVE')
  ) as v(organization_id, person_id, role_id, status)
  where not exists (
    select 1 from public.organization_memberships m
    where m.organization_id = v.organization_id and m.person_id = v.person_id and m.status = 'ACTIVE'
  );

  insert into public.pets (created_by_user_id, name, species_code, sex, size, home_locality_id, birth_precision)
  select * from (values
    (u_public, 'QA • Luna', 'DOG', 'FEMALE', 'MEDIUM', loc_palermo, 'UNKNOWN'),
    (u_private, 'QA • Mora', 'CAT', 'FEMALE', 'SMALL', loc_sv, 'UNKNOWN'),
    (u_public, 'QA • Coco Salud', 'DOG', 'MALE', 'LARGE', loc_caba, 'UNKNOWN'),
    (u_public, 'QA • Nube Foto', 'CAT', 'MALE', 'SMALL', loc_palermo, 'UNKNOWN'),
    (u_rescuer, 'QA • Perdido', 'DOG', 'MALE', 'MEDIUM', loc_ab, 'UNKNOWN'),
    (u_rescuer, 'QA • Encontrado', 'CAT', 'UNKNOWN', 'SMALL', loc_caba, 'UNKNOWN'),
    (u_org_admin, 'QA • Adopción', 'DOG', 'FEMALE', 'SMALL', loc_sv, 'UNKNOWN'),
    (u_org_admin, 'QA • Org Responsable', 'DOG', 'MALE', 'MEDIUM', loc_sv, 'UNKNOWN')
  ) as v(created_by_user_id, name, species_code, sex, size, home_locality_id, birth_precision)
  where not exists (select 1 from public.pets p where p.name = v.name);

  select id into pet_a from public.pets where name = 'QA • Luna' limit 1;
  select id into pet_b from public.pets where name = 'QA • Mora' limit 1;
  select id into pet_c from public.pets where name = 'QA • Coco Salud' limit 1;
  select id into pet_d from public.pets where name = 'QA • Nube Foto' limit 1;
  select id into pet_e from public.pets where name = 'QA • Perdido' limit 1;
  select id into pet_f from public.pets where name = 'QA • Encontrado' limit 1;
  select id into pet_g from public.pets where name = 'QA • Adopción' limit 1;
  select id into pet_h from public.pets where name = 'QA • Org Responsable' limit 1;

  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_person_id, holder_organization_id, role, status, granted_by_actor_user_id
  ) values
    (pet_a, 'PERSON', u_public, null, 'OWNER', 'ACTIVE', u_public),
    (pet_b, 'PERSON', u_private, null, 'OWNER', 'ACTIVE', u_private),
    (pet_b, 'PERSON', u_second, null, 'OWNER', 'ACTIVE', u_private),
    (pet_b, 'PERSON', u_rescuer, null, 'AUTHORIZED', 'ACTIVE', u_private),
    (pet_c, 'PERSON', u_public, null, 'OWNER', 'ACTIVE', u_public),
    (pet_d, 'PERSON', u_public, null, 'OWNER', 'ACTIVE', u_public),
    (pet_e, 'PERSON', u_rescuer, null, 'OWNER', 'ACTIVE', u_rescuer),
    (pet_f, 'PERSON', u_rescuer, null, 'OWNER', 'ACTIVE', u_rescuer),
    (pet_g, 'PERSON', u_org_admin, null, 'OWNER', 'ACTIVE', u_org_admin),
    (pet_h, 'ORGANIZATION', null, org_shelter, 'RESPONSIBLE', 'ACTIVE', u_org_admin)
  on conflict do nothing;

  insert into public.pet_declared_health (pet_id, notes, updated_by)
  values (pet_c, 'QA health pack', u_public)
  on conflict (pet_id) do update set notes = excluded.notes;
  insert into public.pet_allergies (pet_id, name, source, actor_user_id)
  select pet_c, 'QA pollo', 'DECLARED', u_public
  where not exists (select 1 from public.pet_allergies where pet_id = pet_c and name = 'QA pollo');
  insert into public.pet_medications (pet_id, name, instructions, source, actor_user_id)
  select pet_c, 'QA prednisona', 'QA only', 'DECLARED', u_public
  where not exists (select 1 from public.pet_medications where pet_id = pet_c and name = 'QA prednisona');
  insert into public.pet_declared_vaccinations (pet_id, vaccine_name, administered_on, actor_user_id)
  select pet_c, 'QA antirrábica', current_date - 30, u_public
  where not exists (select 1 from public.pet_declared_vaccinations where pet_id = pet_c);
  insert into public.pet_parasite_treatments (pet_id, kind, product_name, treated_on, source, actor_user_id)
  select pet_c, 'DEWORMING', 'QA desparasitario', current_date - 10, 'DECLARED', u_public
  where not exists (select 1 from public.pet_parasite_treatments where pet_id = pet_c);
  insert into public.pet_conditions (pet_id, name, source, actor_user_id)
  select pet_c, 'QA otitis', 'DECLARED', u_public
  where not exists (select 1 from public.pet_conditions where pet_id = pet_c);

  insert into public.vitacora_profiles (pet_id, visibility)
  values (pet_c, 'PUBLIC_REDACTED')
  on conflict (pet_id) do update set visibility = 'PUBLIC_REDACTED';
  insert into public.vitacora_moments (pet_id, kind, title, body, visibility, created_by)
  select pet_c, 'NOTE', 'QA llegada', 'Historia básica QA.', 'SHARED', u_public
  where not exists (select 1 from public.vitacora_moments where pet_id = pet_c and title = 'QA llegada');

  insert into public.lost_found_alerts (kind, pet_id, created_by, locality_id, status)
  select 'LOST', pet_e, u_rescuer, loc_ab, 'OPEN'
  where not exists (select 1 from public.lost_found_alerts where pet_id = pet_e and kind = 'LOST');
  insert into public.lost_found_alerts (kind, pet_id, created_by, locality_id, status)
  select 'FOUND', pet_f, u_rescuer, loc_caba, 'OPEN'
  where not exists (select 1 from public.lost_found_alerts where pet_id = pet_f and kind = 'FOUND');

  insert into public.adoption_publications (pet_id, published_by, organization_id, status)
  select pet_g, u_org_admin, org_shelter, 'OPEN'
  where not exists (select 1 from public.adoption_publications where pet_id = pet_g and status = 'OPEN');
  insert into public.adoption_publications (pet_id, published_by, organization_id, status)
  select pet_h, u_org_admin, org_shelter, 'HIDDEN'
  where not exists (select 1 from public.adoption_publications where pet_id = pet_h and status = 'HIDDEN');

  insert into public.social_posts (author_user_id, body, visibility)
  select u_public, 'QA post público desde CABA.', 'PUBLIC'
  where not exists (select 1 from public.social_posts where author_user_id = u_public and body like 'QA post público%');
  insert into public.social_posts (author_user_id, body, visibility)
  select u_private, 'QA post de perfil privado.', 'FOLLOWERS'
  where not exists (select 1 from public.social_posts where author_user_id = u_private and body like 'QA post de perfil%');
  insert into public.social_posts (author_user_id, body, visibility)
  select u_rescuer, 'QA aviso de búsqueda.', 'PUBLIC'
  where not exists (select 1 from public.social_posts where author_user_id = u_rescuer and body like 'QA aviso%');

  insert into public.conversations (subject_kind, created_by)
  select 'PERSON', u_public
  where not exists (
    select 1 from public.conversations c
    join public.conversation_participants a on a.conversation_id = c.id and a.person_id = u_public
    join public.conversation_participants b on b.conversation_id = c.id and b.person_id = u_second
  );
  select c.id into conv
  from public.conversations c
  join public.conversation_participants a on a.conversation_id = c.id and a.person_id = u_public
  join public.conversation_participants b on b.conversation_id = c.id and b.person_id = u_second
  limit 1;
  if conv is null then
    insert into public.conversations (subject_kind, created_by) values ('PERSON', u_public) returning id into conv;
    insert into public.conversation_participants (conversation_id, participant_kind, person_id)
    values (conv, 'PERSON', u_public), (conv, 'PERSON', u_second);
  end if;
  insert into public.messages (conversation_id, actor_user_id, body)
  select conv, u_public, 'QA hola — mensaje persona a persona.'
  where not exists (select 1 from public.messages where conversation_id = conv and body like 'QA hola%');

  insert into public.service_providers (holder_kind, holder_person_id, holder_organization_id, display_name)
  select 'ORGANIZATION', null, org_vet, 'QA • Veterinaria Palermo'
  where not exists (select 1 from public.service_providers where display_name = 'QA • Veterinaria Palermo');
  insert into public.service_providers (holder_kind, holder_person_id, holder_organization_id, display_name)
  select 'PERSON', u_vet, null, 'QA • Veterinaria San Vicente'
  where not exists (select 1 from public.service_providers where display_name = 'QA • Veterinaria San Vicente');
  insert into public.service_providers (holder_kind, holder_person_id, holder_organization_id, display_name)
  select 'PERSON', u_provider, null, 'QA • Paseos Palermo'
  where not exists (select 1 from public.service_providers where display_name = 'QA • Paseos Palermo');
  insert into public.service_providers (holder_kind, holder_person_id, holder_organization_id, display_name)
  select 'PERSON', u_rescuer, null, 'QA • Paseos Almirante Brown'
  where not exists (select 1 from public.service_providers where display_name = 'QA • Paseos Almirante Brown');
  insert into public.service_providers (holder_kind, holder_person_id, holder_organization_id, display_name)
  select 'PERSON', u_daycare, null, 'QA • Educador Córdoba'
  where not exists (select 1 from public.service_providers where display_name = 'QA • Educador Córdoba');
  insert into public.service_providers (holder_kind, holder_organization_id, display_name)
  select 'ORGANIZATION', org_day, 'QA • Guardería Córdoba'
  where not exists (select 1 from public.service_providers where display_name = 'QA • Guardería Córdoba');

  select id into prov_caba from public.service_providers where display_name = 'QA • Veterinaria Palermo';
  select id into prov_ba from public.service_providers where display_name = 'QA • Veterinaria San Vicente';
  select id into prov_walk from public.service_providers where display_name = 'QA • Paseos Palermo';
  select id into prov_ab from public.service_providers where display_name = 'QA • Paseos Almirante Brown';
  select id into prov_cba from public.service_providers where display_name = 'QA • Educador Córdoba';
  select id into prov_sf from public.service_providers where display_name = 'QA • Guardería Córdoba';

  insert into public.service_offerings (provider_id, category_code, name)
  select prov_caba, 'VETERINARY', 'Consulta QA CABA' where not exists (select 1 from public.service_offerings where provider_id = prov_caba);
  insert into public.service_offerings (provider_id, category_code, name)
  select prov_ba, 'VETERINARY', 'Consulta QA BA' where not exists (select 1 from public.service_offerings where provider_id = prov_ba);
  insert into public.service_offerings (provider_id, category_code, name)
  select prov_walk, 'WALKING', 'Paseo QA CABA' where not exists (select 1 from public.service_offerings where provider_id = prov_walk);
  insert into public.service_offerings (provider_id, category_code, name)
  select prov_ab, 'WALKING', 'Paseo QA AB' where not exists (select 1 from public.service_offerings where provider_id = prov_ab);
  insert into public.service_offerings (provider_id, category_code, name)
  select prov_cba, 'TRAINING', 'Educación QA CBA' where not exists (select 1 from public.service_offerings where provider_id = prov_cba);
  insert into public.service_offerings (provider_id, category_code, name)
  select prov_sf, 'BOARDING', 'Guardería QA' where not exists (select 1 from public.service_offerings where provider_id = prov_sf);

  insert into public.provider_coverage_areas (provider_id, locality_id) values
    (prov_caba, loc_palermo),
    (prov_ba, loc_sv),
    (prov_walk, loc_caba),
    (prov_ab, loc_ab),
    (prov_cba, loc_cba),
    (prov_sf, loc_sf)
  on conflict (provider_id, locality_id) do nothing;
end$$;
