-- QA-01 reset. Deletes ONLY QA-01 tagged fixtures. Never touches velu.

do $$
declare
  v_qa_users uuid[];
begin
  if exists (select 1 from public.persons where lower(username) = 'velu') then
    -- keep velu even if a future fixture reused the name
    null;
  end if;

  select coalesce(array_agg(user_id), '{}') into v_qa_users
  from public.persons
  where username like 'qa\_%' escape '\'
    and lower(username) <> 'velu';

  delete from public.messages
  where actor_user_id = any (v_qa_users)
     or conversation_id in (
       select c.id from public.conversations c
       where c.created_by = any (v_qa_users)
     );

  delete from public.conversation_participants
  where person_id = any (v_qa_users)
     or conversation_id in (
       select c.id from public.conversations c where c.created_by = any (v_qa_users)
     );

  delete from public.conversations where created_by = any (v_qa_users);

  delete from public.social_reactions where user_id = any (v_qa_users);
  delete from public.social_comments where author_user_id = any (v_qa_users);
  delete from public.social_posts where author_user_id = any (v_qa_users);

  delete from public.friendships
  where requester_id = any (v_qa_users) or addressee_id = any (v_qa_users);

  delete from public.adoption_applications
  where applicant_user_id = any (v_qa_users)
     or publication_id in (
       select id from public.adoption_publications where published_by = any (v_qa_users)
     );
  delete from public.adoption_publications where published_by = any (v_qa_users);

  delete from public.lost_found_alerts where created_by = any (v_qa_users);

  delete from public.pet_allergies where pet_id in (
    select id from public.pets where created_by_user_id = any (v_qa_users) or name like 'QA •%'
  );
  delete from public.pet_medications where pet_id in (
    select id from public.pets where created_by_user_id = any (v_qa_users) or name like 'QA •%'
  );
  delete from public.pet_declared_vaccinations where pet_id in (
    select id from public.pets where created_by_user_id = any (v_qa_users) or name like 'QA •%'
  );
  delete from public.pet_parasite_treatments where pet_id in (
    select id from public.pets where created_by_user_id = any (v_qa_users) or name like 'QA •%'
  );
  delete from public.pet_conditions where pet_id in (
    select id from public.pets where created_by_user_id = any (v_qa_users) or name like 'QA •%'
  );
  delete from public.pet_declared_health where pet_id in (
    select id from public.pets where created_by_user_id = any (v_qa_users) or name like 'QA •%'
  );
  delete from public.vitacora_moments where pet_id in (
    select id from public.pets where created_by_user_id = any (v_qa_users) or name like 'QA •%'
  );
  delete from public.vitacora_profiles where pet_id in (
    select id from public.pets where created_by_user_id = any (v_qa_users) or name like 'QA •%'
  );

  delete from public.provider_coverage_areas
  where provider_id in (
    select id from public.service_providers
    where display_name like 'QA •%'
       or holder_person_id = any (v_qa_users)
       or holder_organization_id in (select id from public.organizations where slug like 'qa-%')
  );
  delete from public.service_offerings
  where provider_id in (
    select id from public.service_providers
    where display_name like 'QA •%'
       or holder_person_id = any (v_qa_users)
       or holder_organization_id in (select id from public.organizations where slug like 'qa-%')
  );
  delete from public.service_providers
  where display_name like 'QA •%'
     or holder_person_id = any (v_qa_users)
     or holder_organization_id in (select id from public.organizations where slug like 'qa-%');

  delete from public.pet_responsibility_links
  where pet_id in (
    select id from public.pets where created_by_user_id = any (v_qa_users) or name like 'QA •%'
  );
  delete from public.pets
  where created_by_user_id = any (v_qa_users) or name like 'QA •%';

  delete from public.organization_memberships
  where organization_id in (select id from public.organizations where slug like 'qa-%');
  delete from public.organization_capabilities
  where organization_id in (select id from public.organizations where slug like 'qa-%');
  delete from public.organization_public_profiles
  where organization_id in (select id from public.organizations where slug like 'qa-%');
  delete from public.organization_roles
  where organization_id in (select id from public.organizations where slug like 'qa-%');
  delete from public.organizations where slug like 'qa-%';

  update public.persons
  set display_name = display_name
  where username like 'qa\_%' escape '\'
    and lower(username) <> 'velu';
end$$;
