-- Read-only validation for QA community-care actors. STAGING only.

select 'AUTH_QA_USERS' as k, count(*)::bigint as n
  from auth.users
 where lower(email) ~ '^qa(0[1-9]|1[0-6])\.'
union all
select 'PERSONS_QA', count(*)
  from public.persons
 where username in (
   'qa01owner','qa02finder','qa03rescuer','qa04rescuer2','qa05unavailable',
   'qa06foster','qa07shelter','qa08pending','qa09noreq','qa10vet',
   'qa11proa','qa12proind','qa13shop','qa14adopter','qa15adopter2','qa16vetnorte'
 )
union all
select 'USERNAME_UNIQUE', count(distinct lower(username))
  from public.persons
 where username in (
   'qa01owner','qa02finder','qa03rescuer','qa04rescuer2','qa05unavailable',
   'qa06foster','qa07shelter','qa08pending','qa09noreq','qa10vet',
   'qa11proa','qa12proind','qa13shop','qa14adopter','qa15adopter2','qa16vetnorte'
 )
union all
select 'ORGS_QA_CORE', count(*)
  from public.organizations
 where slug in (
   'qa-cc-shelter-verified', 'qa-cc-shelter-pending', 'qa-cc-shelter-noreq',
   'qa-cc-vet-centro', 'qa-cc-petshop-centro', 'qa-cc-vet-norte'
 )
union all
select 'PETS_QA_CORE', count(*)
  from public.pets
 where name in ('QA - Luna Adopcion', 'QA - Bruno Transito', 'QA - Mora', 'QA - Milo')
union all
select 'VITACORA_QA_CORE', count(*)
  from public.vitacora_profiles v
  join public.pets p on p.id = v.pet_id
 where p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito', 'QA - Mora', 'QA - Milo')
union all
select 'ADOPTION_OPEN_LUNA', count(*)
  from public.adoption_publications a
  join public.pets p on p.id = a.pet_id
 where p.name = 'QA - Luna Adopcion' and a.status = 'OPEN'
union all
select 'FOSTER_PLACEMENTS', count(*)
  from public.foster_placements
union all
select 'FOSTER_REQUESTS', count(*)
  from public.foster_care_requests
union all
select 'LOST_FOUND_OPEN', count(*)
  from public.lost_found_alerts
 where status in ('OPEN', 'CLAIMED')
union all
select 'ADOPTION_APPLICATIONS', count(*)
  from public.adoption_applications
union all
select 'QA03_ELIGIBLE', count(*)
  from public.persons p
  join public.person_capabilities c on c.user_id = p.user_id
 where p.username = 'qa03rescuer'
   and c.capability = 'RESCUER' and c.active and c.verification_status = 'VERIFIED'
   and p.receive_nearby_cases and p.base_location is not null
union all
select 'QA04_ELIGIBLE', count(*)
  from public.persons p
  join public.person_capabilities c on c.user_id = p.user_id
 where p.username = 'qa04rescuer2'
   and c.capability = 'RESCUER' and c.active and c.verification_status = 'VERIFIED'
   and p.receive_nearby_cases and p.base_location is not null
union all
select 'QA05_EXCLUDED', count(*)
  from public.persons p
  join public.person_capabilities c on c.user_id = p.user_id
 where p.username = 'qa05unavailable'
   and c.capability = 'RESCUER' and c.verification_status = 'VERIFIED'
   and coalesce(p.receive_nearby_cases, false) = false
union all
select 'QA06_FOSTER_NOT_FOUND', count(*)
  from public.persons p
  join public.person_capabilities c on c.user_id = p.user_id
 where p.username = 'qa06foster'
   and c.capability = 'FOSTER' and c.verification_status = 'VERIFIED'
   and not exists (
     select 1 from public.person_capabilities r
      where r.user_id = p.user_id and r.capability = 'RESCUER' and r.active
   )
union all
select 'QA07_VERIFIED', count(*)
  from public.organizations o
 where o.slug = 'qa-cc-shelter-verified' and o.verification_status = 'VERIFIED'
union all
select 'QA08_PENDING', count(*)
  from public.organizations o
 where o.slug = 'qa-cc-shelter-pending' and o.verification_status = 'PENDING'
union all
select 'QA09_NOT_REQUESTED', count(*)
  from public.organizations o
 where o.slug = 'qa-cc-shelter-noreq' and o.verification_status = 'NOT_REQUESTED'
  and not exists (
    select 1 from public.leover_verification_requests r
     where r.organization_id = o.id
  )
union all
select 'QA10_VERIFIED', count(*)
  from public.organizations o
 where o.slug = 'qa-cc-vet-centro' and o.verification_status = 'VERIFIED'
union all
select 'QA13_UNVERIFIED', count(*)
  from public.organizations o
 where o.slug = 'qa-cc-petshop-centro' and o.verification_status = 'NOT_REQUESTED'
union all
select 'MORA_PRIVATE_CARE', count(*)
  from public.veterinary_care_records r
  join public.pets p on p.id = r.pet_id
  join public.organizations o on o.id = r.organization_id
 where p.name = 'QA - Mora' and o.slug = 'qa-cc-vet-centro'
union all
select 'MORA_PENDING_PROPOSAL', count(*)
  from public.vitacora_update_proposals pr
  join public.pets p on p.id = pr.pet_id
 where p.name = 'QA - Mora' and pr.status = 'PENDING'
union all
select 'MORA_ACCEPTED_VISIBLE', count(*)
  from public.vitacora_integration_links l
  join public.pets p on p.id = l.pet_id
 where p.name = 'QA - Mora' and l.visible
union all
select 'STAFF_PERSONS', count(*)
  from public.persons p
  join public.platform_admin_identities i on i.user_id = p.user_id
union all
select 'STAFF_AUTH', count(*)
  from auth.users u
  join public.platform_admin_identities i on i.user_id = u.id;
