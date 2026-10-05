-- LeoVer 17B.12 STAGING fixture. Not a production migration.
-- Reuses QA Refugio Norte (A) and QA Refugio Sur (B) from seed_community_care_02.sql.
-- Prefix QA17B12. Idempotent. Does not create extra auth users.
-- Run only through scripts/qa/seed-17b12-staging.ps1, which refuses every project
-- other than STAGING tobqbddfcyitwgbkthhy.

do $$
declare
  v_a uuid;
  v_b uuid;
  v_owner uuid;
  v_adopter uuid;
begin
  select id into v_a from public.organizations where slug = 'qa-cc02-shelter-n';
  select id into v_b from public.organizations where slug = 'qa-cc02-shelter-u';
  if v_a is null or v_b is null then
    raise exception 'QA17B12_ABORT_ORGS_MISSING';
  end if;
  select user_id into v_owner from public.persons where username = 'qa07shelter';
  select user_id into v_adopter from public.persons where username = 'qa14adopter';

  if to_regclass('public.m17_donation_campaigns') is not null then
    insert into public.m17_donation_campaigns (
      organization_id, title, description, campaign_type, campaign_status,
      goal_amount_minor, currency, public_location_text, moderation_status, published_at
    )
    select v_a, 'QA17B12 Aporte Norte', 'Campaña monetaria del refugio A para controles veterinarios.',
           'MEDICAL', 'PUBLISHED', 15000000, 'ARS', 'Belgrano, CABA', 'APPROVED', timezone('utc', now())
    where not exists (
      select 1 from public.m17_donation_campaigns where title = 'QA17B12 Aporte Norte'
    );
    insert into public.m17_donation_campaigns (
      organization_id, title, description, campaign_type, campaign_status,
      goal_amount_minor, currency, public_location_text, moderation_status, published_at
    )
    select v_b, 'QA17B12 Aporte Sur', 'Campaña monetaria del refugio B para alimento.',
           'FOOD_AND_SUPPLIES', 'PUBLISHED', 8000000, 'ARS', 'Avellaneda', 'APPROVED', timezone('utc', now())
    where not exists (
      select 1 from public.m17_donation_campaigns where title = 'QA17B12 Aporte Sur'
    );
  end if;

  if to_regclass('public.m17_in_kind_needs') is not null then
    insert into public.m17_in_kind_needs (
      organization_id, category, title, description, quantity_needed, quantity_unit,
      status, public_location_text, moderation_status, published_at
    )
    select v_a, 'FOOD', 'QA17B12 Alimento Norte', 'Balanceado para perros del refugio A.',
           20, 'bolsas', 'PUBLISHED', 'Belgrano, CABA', 'APPROVED', timezone('utc', now())
    where not exists (select 1 from public.m17_in_kind_needs where title = 'QA17B12 Alimento Norte');
    insert into public.m17_in_kind_needs (
      organization_id, category, title, description, quantity_needed, quantity_unit,
      status, public_location_text, moderation_status, published_at
    )
    select v_b, 'HYGIENE', 'QA17B12 Higiene Sur', 'Mantas y limpieza del refugio B.',
           10, 'unidades', 'PUBLISHED', 'Avellaneda', 'APPROVED', timezone('utc', now())
    where not exists (select 1 from public.m17_in_kind_needs where title = 'QA17B12 Higiene Sur');
  end if;

  if to_regclass('public.m17_volunteer_opportunities') is not null then
    insert into public.m17_volunteer_opportunities (
      organization_id, opportunity_type, title, description, required_people, accepted_people,
      status, public_location_text, moderation_status, published_at
    )
    select * from (values
      (v_a, 'ANIMAL_CARE', 'QA17B12 Paseos Norte', 'Paseos de perros del refugio A. Hay lugares.', 6, 0, 'PUBLISHED', 'Belgrano, CABA'),
      (v_a, 'SHELTER_SUPPORT', 'QA17B12 Cupo Norte', 'Turno de limpieza del refugio A. Cupo completo.', 2, 2, 'FILLED', 'Belgrano, CABA'),
      (v_b, 'EVENTS', 'QA17B12 Feria Sur', 'Feria del refugio B. Sin postulaciones todavía.', 4, 0, 'PUBLISHED', 'Avellaneda'),
      (v_b, 'TRANSPORT', 'QA17B12 Traslado Sur', 'Traslados del refugio B. Hay lugares.', 3, 1, 'PUBLISHED', 'Avellaneda')
    ) as seed(organization_id, opportunity_type, title, description, required_people, accepted_people, status, public_location_text)
    where not exists (
      select 1 from public.m17_volunteer_opportunities existing
      where existing.title = seed.title
    );
    -- moderation and published_at are filled by a second update so the values() list stays small
    update public.m17_volunteer_opportunities
       set moderation_status = 'APPROVED',
           published_at = coalesce(published_at, timezone('utc', now()))
     where title like 'QA17B12 %';
  end if;

  if to_regclass('public.lost_found_alerts') is not null and v_owner is not null then
    insert into public.lost_found_alerts (kind, created_by, status, created_at)
    select 'LOST', v_owner, 'OPEN', timezone('utc', now()) - interval '30 days'
    where not exists (
      select 1 from public.lost_found_alerts
      where created_by = v_owner and kind = 'LOST' and status = 'OPEN'
        and created_at < timezone('utc', now()) - interval '7 days'
    );
    insert into public.lost_found_alerts (kind, created_by, status, created_at)
    select 'FOUND', v_owner, 'OPEN', timezone('utc', now())
    where not exists (
      select 1 from public.lost_found_alerts
      where created_by = v_owner and kind = 'FOUND' and status = 'OPEN'
        and created_at > timezone('utc', now()) - interval '1 day'
    );
  end if;

  if to_regclass('public.service_providers') is not null and v_owner is not null then
    insert into public.service_providers (holder_kind, holder_person_id, display_name, lifecycle_status)
    select 'PERSON', v_owner, name, 'ACTIVE'
    from (values
      ('QA17B12 Veterinaria Norte'),
      ('QA17B12 Peluquería Sur'),
      ('QA17B12 Paseador Oeste')
    ) as names(name)
    where not exists (
      select 1 from public.service_providers existing where existing.display_name = names.name
    );
  end if;

  raise notice 'QA17B12_SEED_OK a=% b=% adopter=%', v_a, v_b, v_adopter;
end $$;
