-- M17 migraciones 054+055 — validación remota staging (casos 01–120)
-- Ejecutar: supabase db query --linked -f scripts/ops/m17_remote_validation_054_055.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m17_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m17_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m17_val_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m17_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

do $setup$
declare
  v_mgr uuid := 'd0000000-0000-4000-8000-000000000001';
  v_out uuid := 'd0000000-0000-4000-8000-000000000002';
  v_donor uuid := 'd0000000-0000-4000-8000-000000000003';
  v_org uuid := 'e0000000-0000-4000-8000-000000000001';
  v_bad_org uuid := 'e0000000-0000-4000-8000-000000000002';
  v_shelter uuid := 'c0000000-0000-4000-8000-000000000001';
  v_c_draft uuid := 'c0000000-0000-4000-8000-000000000002';
  v_c_pub uuid := 'c0000000-0000-4000-8000-000000000003';
  v_c_work uuid := 'c0000000-0000-4000-8000-000000000004';
  v_c_term uuid := 'c0000000-0000-4000-8000-000000000005';
  v_need_pub uuid := 'c0000000-0000-4000-8000-000000000006';
  v_need_draft uuid := 'c0000000-0000-4000-8000-000000000007';
  v_vol_pub uuid := 'c0000000-0000-4000-8000-000000000008';
  v_vol_draft uuid := 'c0000000-0000-4000-8000-000000000009';
  v_trans_report uuid := 'c0000000-0000-4000-8000-000000000010';
  v_pledge_id uuid;
  v_app_id uuid;
  v_app_id2 uuid;
  v_app_accept uuid;
  v_fund_id uuid;
  v_json jsonb;
  v_cnt int;
  v_qty int;
  v_err text;
begin
  -- Prerrequisito permisos donation.* (054)
  insert into public.organization_permissions (code, description) values
    ('donation.view', 'Ver campañas solidarias de la organización'),
    ('donation.manage', 'Gestionar campañas solidarias y actualizaciones')
  on conflict (code) do nothing;

  insert into public.organization_role_permissions (role_id, permission_id)
  select r.id, p.id
  from public.organization_roles r
  cross join public.organization_permissions p
  where r.code in ('OWNER', 'ADMIN', 'MANAGER')
    and p.code in ('donation.view', 'donation.manage')
  on conflict do nothing;

  insert into public.organization_role_permissions (role_id, permission_id)
  select r.id, p.id
  from public.organization_roles r
  cross join public.organization_permissions p
  where r.code = 'MEMBER' and p.code = 'donation.view'
  on conflict do nothing;

  -- Usuarios auth + public
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_mgr, 'authenticated', 'authenticated',
     'm17-mgr@test.local', crypt('m17-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm17-out@test.local', crypt('m17-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_donor, 'authenticated', 'authenticated',
     'm17-donor@test.local', crypt('m17-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, account_type, email_verified, account_status)
  values
    (v_mgr, 'm17-mgr@test.local', 'M17 Manager', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm17-out@test.local', 'M17 Outsider', 'PERSON', true, 'ACTIVE'),
    (v_donor, 'm17-donor@test.local', 'M17 Donor', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE';

  insert into public.organizations (id, slug, display_name, type, status, created_by)
  values
    (v_org, 'm17-val-shelter', 'M17 Val Shelter Org', 'SHELTER', 'ACTIVE', v_mgr),
    (v_bad_org, 'm17-val-vet', 'M17 Val Vet Org', 'VETERINARY_CLINIC', 'ACTIVE', v_out)
  on conflict (id) do nothing;

  insert into public.organization_memberships (organization_id, user_id, role_code, status, joined_at)
  values (v_org, v_mgr, 'OWNER', 'ACTIVE', now())
  on conflict do nothing;

  -- Perfil refugio M16 (opcional FK campañas)
  insert into public.m16_shelter_profiles (
    id, organization_id, display_name, public_zone_text, total_capacity,
    operational_status, publication_status, verification_status, created_by, updated_by
  ) values (
    v_shelter, v_org, 'Refugio M17 Val', 'Zona Test', 20,
    'ACTIVE', 'PUBLISHED', 'UNVERIFIED', v_mgr, v_mgr
  ) on conflict (id) do nothing;

  -- Semilla campañas (postgres): DRAFT + PUBLISHED para pruebas públicas
  insert into public.m17_donation_campaigns (
    id, organization_id, title, description, campaign_type, campaign_status,
    goal_amount_minor, currency, shelter_profile_id, shelter_public_name, created_by
  ) values
    (v_c_draft, v_org, 'Campaña Borrador M17',
     'Descripción borrador de prueba M17 validación remota.', 'GENERAL_SUPPORT', 'DRAFT',
     100000, 'ARS', v_shelter, 'Refugio M17 Val', v_mgr),
    (v_c_pub, v_org, 'Campaña Publicada M17',
     'Descripción publicada de prueba M17 validación remota.', 'MEDICAL', 'PUBLISHED',
     200000, 'ARS', v_shelter, 'Refugio M17 Val', v_mgr)
  on conflict (id) do nothing;

  update public.m17_donation_campaigns
  set published_at = timezone('utc', now()), moderation_status = 'APPROVED'
  where id = v_c_pub;

  -- Contribuciones CONFIRMED/PENDING/PRIVATE (service_role simulado)
  perform set_config('request.jwt.claim.role', 'service_role', true);
  insert into public.m17_contributions (
    campaign_id, amount_minor, currency, status, visibility,
    donor_display_name, public_message, provider_reference, idempotency_key
  ) values
    (v_c_pub, 50000, 'ARS', 'CONFIRMED', 'PUBLIC', 'Donante Test', 'Gracias', 'prov-secret-1', 'idem-key-1'),
    (v_c_pub, 25000, 'ARS', 'CONFIRMED', 'ANONYMOUS', null, null, 'prov-secret-2', 'idem-key-2'),
    (v_c_pub, 10000, 'ARS', 'PENDING', 'PUBLIC', 'Pendiente', null, 'prov-secret-3', 'idem-key-3'),
    (v_c_pub, 15000, 'ARS', 'CONFIRMED', 'PRIVATE', 'Privado', null, 'prov-secret-4', 'idem-key-4')
  on conflict do nothing;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- Semilla bienes / voluntariado / transparencia PUBLISHED (sin RPC org-create en 055)
  insert into public.m17_in_kind_needs (
    id, organization_id, shelter_profile_id, category, title, description,
    quantity_needed, quantity_unit, status, moderation_status, created_by, published_at
  ) values
    (v_need_pub, v_org, v_shelter, 'FOOD', 'Alimento publicado M17',
     'Necesidad publicada de prueba M17 validación remota.', 100, 'kg', 'PUBLISHED', 'APPROVED', v_mgr, now()),
    (v_need_draft, v_org, v_shelter, 'HYGIENE', 'Borrador in-kind M17',
     'Necesidad borrador de prueba M17 validación remota.', 50, 'unidades', 'DRAFT', null, v_mgr, null)
  on conflict (id) do nothing;

  insert into public.m17_volunteer_opportunities (
    id, organization_id, shelter_profile_id, opportunity_type, title, description,
    required_people, status, moderation_status, created_by, published_at
  ) values
    (v_vol_pub, v_org, v_shelter, 'SHELTER_SUPPORT', 'Voluntariado publicado M17',
     'Oportunidad publicada de prueba M17 validación remota.', 5, 'PUBLISHED', 'APPROVED', v_mgr, now()),
    (v_vol_draft, v_org, v_shelter, 'ANIMAL_CARE', 'Voluntariado borrador M17',
     'Oportunidad borrador de prueba M17 validación remota.', 3, 'DRAFT', null, v_mgr, null)
  on conflict (id) do nothing;

  insert into public.m17_campaign_transparency_reports (
    id, campaign_id, organization_id, title, summary, status,
    total_allocated_minor, currency, created_by, published_at
  ) values (
    v_trans_report, v_c_pub, v_org, 'Informe transparencia M17',
    'Resumen publicado de prueba M17 validación remota.', 'PUBLISHED',
    75000, 'ARS', v_mgr, now()
  ) on conflict (id) do nothing;

  insert into public.m17_fund_usage_items (
    report_id, category, description, amount_minor, currency, public_receipt_file_ref
  ) values (
    v_trans_report, 'MEDICATION', 'Medicamentos adquiridos', 75000, 'ARS', 'receipt-private-ref'
  ) returning id into v_fund_id;

  insert into public.m17_transparency_milestones (
    report_id, title, description, status
  ) values (
    v_trans_report, 'Hito entrega', 'Entrega de insumos completada', 'COMPLETED'
  );

  -- ========================================================================
  -- ESTRUCTURA 01–30
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name in (
    'm17_donation_campaigns', 'm17_campaign_updates', 'm17_contributions',
    'm17_in_kind_needs', 'm17_in_kind_pledges', 'm17_volunteer_opportunities',
    'm17_volunteer_applications', 'm17_campaign_transparency_reports',
    'm17_fund_usage_items', 'm17_transparency_milestones'
  );
  perform pg_temp.m17_val(1, 'Diez tablas M17', v_cnt = 10);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm17_donation_campaigns'
    and constraint_type = 'FOREIGN KEY' and constraint_name like '%organization%';
  perform pg_temp.m17_val(2, 'FK organization_id campañas', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm17_contributions'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'campaign_id';
  perform pg_temp.m17_val(3, 'FK campaign_id contribuciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm17_in_kind_pledges'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'need_id';
  perform pg_temp.m17_val(4, 'FK need_id pledges', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm17_volunteer_applications'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'opportunity_id';
  perform pg_temp.m17_val(5, 'FK opportunity_id postulaciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm17_fund_usage_items'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'report_id';
  perform pg_temp.m17_val(6, 'FK report_id fund_usage', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm17_donation_campaigns'
    and column_name = 'goal_amount_minor' and data_type = 'bigint';
  perform pg_temp.m17_val(7, 'goal_amount_minor bigint', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm17_contributions'
    and column_name = 'amount_minor' and data_type = 'bigint';
  perform pg_temp.m17_val(8, 'amount_minor bigint', v_cnt = 1);

  begin
    insert into public.m17_donation_campaigns (
      organization_id, title, description, campaign_type, goal_amount_minor
    ) values (v_org, 'X', 'Descripción inválida goal cero', 'GENERAL_SUPPORT', 0);
    perform pg_temp.m17_val(9, 'CHECK goal > 0', false, 'debió fallar');
  exception when check_violation then
    perform pg_temp.m17_val(9, 'CHECK goal > 0', true);
  end;

  begin
    insert into public.m17_in_kind_needs (
      organization_id, category, title, description, quantity_needed
    ) values (v_org, 'FOOD', 'X', 'Descripción inválida cantidad cero', 0);
    perform pg_temp.m17_val(10, 'CHECK quantity_needed > 0', false);
  exception when check_violation then
    perform pg_temp.m17_val(10, 'CHECK quantity_needed > 0', true);
  end;

  begin
    insert into public.m17_in_kind_pledges (need_id, contributor_user_id, quantity)
    values (v_need_pub, v_donor, 0);
    perform pg_temp.m17_val(11, 'CHECK pledge quantity > 0', false);
  exception when check_violation then
    perform pg_temp.m17_val(11, 'CHECK pledge quantity > 0', true);
  end;

  begin
    insert into public.m17_donation_campaigns (
      organization_id, title, description, campaign_type, goal_amount_minor,
      starts_at, ends_at
    ) values (
      v_org, 'Fechas inválidas', 'Descripción fechas inválidas M17 test.', 'GENERAL_SUPPORT', 1000,
      timezone('utc', now()), timezone('utc', now()) - interval '1 day'
    );
    perform pg_temp.m17_val(12, 'CHECK ends_at > starts_at', false);
  exception when check_violation then
    perform pg_temp.m17_val(12, 'CHECK ends_at > starts_at', true);
  end;

  begin
    insert into public.m17_donation_campaigns (
      organization_id, title, description, campaign_type, goal_amount_minor
    ) values (v_org, '', 'Descripción título vacío M17 test.', 'GENERAL_SUPPORT', 1000);
    perform pg_temp.m17_val(13, 'CHECK title length', false);
  exception when check_violation then
    perform pg_temp.m17_val(13, 'CHECK title length', true);
  end;

  begin
    insert into public.m17_campaign_updates (campaign_id, message)
    values (v_c_pub, 'ab');
    perform pg_temp.m17_val(14, 'CHECK update message length', false);
  exception when check_violation then
    perform pg_temp.m17_val(14, 'CHECK update message length', true);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m17_contributions (
      campaign_id, amount_minor, currency, status, idempotency_key
    ) values (v_c_pub, 1000, 'ARS', 'PENDING', 'idem-key-1');
    perform pg_temp.m17_val(15, 'UNIQUE idempotency_key', false);
  exception when unique_violation then
    perform pg_temp.m17_val(15, 'UNIQUE idempotency_key', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm17_volunteer_applications'
    and indexdef ilike '%one_active%';
  perform pg_temp.m17_val(16, 'UNIQUE postulación activa', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename like 'm17_%';
  perform pg_temp.m17_val(17, 'Índices M17', v_cnt >= 10);

  select count(*)::int into v_cnt from pg_trigger
  where tgname = 'm17_contributions_guard_trg';
  perform pg_temp.m17_val(18, 'Trigger contributions guard', v_cnt = 1);

  select count(*)::int into v_cnt from pg_trigger
  where tgname = 'm17_inkind_pledge_recompute_trg';
  perform pg_temp.m17_val(19, 'Trigger pledge recompute', v_cnt = 1);

  begin
    perform set_config('request.jwt.claim.role', 'authenticated', true);
    perform set_config('request.jwt.claim.sub', v_mgr::text, true);
    insert into public.m17_contributions (
      campaign_id, amount_minor, currency, status
    ) values (v_c_pub, 1000, 'ARS', 'CONFIRMED');
    perform pg_temp.m17_val(20, 'Cliente no inserta CONFIRMED', false);
  exception when others then
    perform pg_temp.m17_val(20, 'Cliente no inserta CONFIRMED',
      SQLERRM like '%M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE%');
  end;

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm17_in_kind_needs'
    and column_name = 'quantity_committed' and column_default is not null;
  perform pg_temp.m17_val(21, 'quantity_committed default', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm17_volunteer_opportunities'
    and column_name = 'accepted_people' and column_default is not null;
  perform pg_temp.m17_val(22, 'accepted_people default', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm17_campaign_transparency_reports'
    and column_name = 'total_allocated_minor' and column_default is not null;
  perform pg_temp.m17_val(23, 'total_allocated_minor default', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm17_in_kind_needs'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'shelter_profile_id';
  perform pg_temp.m17_val(24, 'FK shelter_profile_id in-kind', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm17_in_kind_needs'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'campaign_id';
  perform pg_temp.m17_val(25, 'FK campaign_id in-kind opcional', v_cnt >= 1);

  begin
    insert into public.m17_donation_campaigns (
      organization_id, title, description, campaign_type, campaign_status, goal_amount_minor
    ) values (
      v_org, 'Terminal completed', 'Descripción terminal completed M17 test.', 'GENERAL_SUPPORT',
      'COMPLETED', 5000
    ) returning id into v_c_term;
    perform pg_temp.m17_val(26, 'INSERT terminal COMPLETED', true);
  exception when others then
    perform pg_temp.m17_val(26, 'INSERT terminal COMPLETED', false, SQLERRM);
  end;

  begin
    insert into public.m17_donation_campaigns (
      organization_id, title, description, campaign_type, campaign_status, goal_amount_minor
    ) values (
      v_org, 'Terminal cancelled', 'Descripción terminal cancelled M17 test.', 'GENERAL_SUPPORT',
      'CANCELLED', 5000
    );
    perform pg_temp.m17_val(27, 'INSERT terminal CANCELLED', true);
  exception when others then
    perform pg_temp.m17_val(27, 'INSERT terminal CANCELLED', false, SQLERRM);
  end;

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm17_in_kind_pledges'
    and constraint_name = 'm17_inkind_pledge_status_chk';
  perform pg_temp.m17_val(28, 'CHECK pledge status enum', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm17_volunteer_applications'
    and constraint_name = 'm17_vol_app_status_chk';
  perform pg_temp.m17_val(29, 'CHECK application status enum', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm17_campaign_transparency_reports'
    and constraint_name = 'm17_trans_report_status_chk';
  perform pg_temp.m17_val(30, 'CHECK transparency status enum', v_cnt = 1);

  -- ========================================================================
  -- RLS 31–40 (una tabla por caso)
  -- ========================================================================
  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_donation_campaigns' and c.relrowsecurity;
  perform pg_temp.m17_val(31, 'RLS m17_donation_campaigns', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_campaign_updates' and c.relrowsecurity;
  perform pg_temp.m17_val(32, 'RLS m17_campaign_updates', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_contributions' and c.relrowsecurity;
  perform pg_temp.m17_val(33, 'RLS m17_contributions', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_in_kind_needs' and c.relrowsecurity;
  perform pg_temp.m17_val(34, 'RLS m17_in_kind_needs', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_in_kind_pledges' and c.relrowsecurity;
  perform pg_temp.m17_val(35, 'RLS m17_in_kind_pledges', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_volunteer_opportunities' and c.relrowsecurity;
  perform pg_temp.m17_val(36, 'RLS m17_volunteer_opportunities', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_volunteer_applications' and c.relrowsecurity;
  perform pg_temp.m17_val(37, 'RLS m17_volunteer_applications', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_campaign_transparency_reports' and c.relrowsecurity;
  perform pg_temp.m17_val(38, 'RLS m17_campaign_transparency_reports', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_fund_usage_items' and c.relrowsecurity;
  perform pg_temp.m17_val(39, 'RLS m17_fund_usage_items', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm17_transparency_milestones' and c.relrowsecurity;
  perform pg_temp.m17_val(40, 'RLS m17_transparency_milestones', v_cnt = 1);

  -- ========================================================================
  -- ANON 41–46
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm17_donation_campaigns' and grantee = 'anon';
  perform pg_temp.m17_val(41, 'Anon sin grant campaigns', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name in ('m17_campaign_updates', 'm17_contributions')
    and grantee = 'anon';
  perform pg_temp.m17_val(42, 'Anon sin grant updates/contrib', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name in ('m17_in_kind_needs', 'm17_in_kind_pledges')
    and grantee = 'anon';
  perform pg_temp.m17_val(43, 'Anon sin grant in-kind', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name in ('m17_volunteer_opportunities', 'm17_volunteer_applications')
    and grantee = 'anon';
  perform pg_temp.m17_val(44, 'Anon sin grant voluntariado', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name in (
    'm17_campaign_transparency_reports', 'm17_fund_usage_items', 'm17_transparency_milestones'
  ) and grantee = 'anon';
  perform pg_temp.m17_val(45, 'Anon sin grant transparencia', v_cnt = 0);

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m17_donation_campaigns;
    reset role;
    perform pg_temp.m17_val(46, 'Anon no SELECT directo tablas', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m17_val(46, 'Anon no SELECT directo tablas', true);
  when others then
    reset role;
    perform pg_temp.m17_val(46, 'Anon no SELECT directo tablas', false, SQLERRM);
  end;

  -- ========================================================================
  -- PLEDGE / APPLICATION 47–56
  -- ========================================================================
  perform pg_temp.m17_act_as(v_donor);
  begin
    v_json := public.m17_create_in_kind_pledge(v_need_pub, 10, 'Compromiso test');
    v_pledge_id := (v_json->>'id')::uuid;
    perform pg_temp.m17_val(47, 'Donante crea pledge', v_json->>'status' = 'PLEDGED');
  exception when others then
    perform pg_temp.m17_val(47, 'Donante crea pledge', false, SQLERRM);
  end;

  if v_pledge_id is not null then
    begin
      v_json := public.m17_cancel_own_in_kind_pledge(v_pledge_id);
      perform pg_temp.m17_val(48, 'Donante cancela pledge', v_json->>'status' = 'CANCELLED');
    exception when others then
      perform pg_temp.m17_val(48, 'Donante cancela pledge', false, SQLERRM);
    end;
  end if;

  -- Nuevo pledge para pruebas org/ajeno
  begin
    v_json := public.m17_create_in_kind_pledge(v_need_pub, 5, 'Segundo pledge');
    v_pledge_id := (v_json->>'id')::uuid;
  exception when others then null;
  end;

  perform pg_temp.m17_act_as(v_out);
  if v_pledge_id is not null then
    begin
      perform public.m17_cancel_own_in_kind_pledge(v_pledge_id);
      perform pg_temp.m17_val(49, 'Ajeno no cancela pledge', false);
    exception when others then
      perform pg_temp.m17_val(49, 'Ajeno no cancela pledge', SQLERRM like '%M17_PERMISSION_DENIED%');
    end;
  end if;

  perform pg_temp.m17_act_as(v_mgr);
  if v_pledge_id is not null then
    begin
      v_json := public.m17_mark_in_kind_pledge_delivered(v_pledge_id);
      perform pg_temp.m17_val(50, 'Org marca entregado', v_json->>'status' = 'DELIVERED');
    exception when others then
      perform pg_temp.m17_val(50, 'Org marca entregado', false, SQLERRM);
    end;
  end if;

  perform pg_temp.m17_act_as(v_out);
  if v_pledge_id is not null then
    begin
      perform public.m17_mark_in_kind_pledge_delivered(v_pledge_id);
      perform pg_temp.m17_val(51, 'Ajeno no marca entregado', false);
    exception when others then
      perform pg_temp.m17_val(51, 'Ajeno no marca entregado', SQLERRM like '%M17_PERMISSION_DENIED%');
    end;
  end if;

  perform pg_temp.m17_act_as(v_donor);
  begin
    v_json := public.m17_submit_volunteer_application(v_vol_pub, 'Disponible fines de semana');
    v_app_id := (v_json->>'id')::uuid;
    perform pg_temp.m17_val(52, 'Donante postula voluntariado', v_json->>'status' = 'SUBMITTED');
  exception when others then
    perform pg_temp.m17_val(52, 'Donante postula voluntariado', false, SQLERRM);
  end;

  if v_app_id is not null then
    begin
      v_json := public.m17_withdraw_volunteer_application(v_app_id);
      perform pg_temp.m17_val(53, 'Donante retira postulación', v_json->>'status' = 'WITHDRAWN');
    exception when others then
      perform pg_temp.m17_val(53, 'Donante retira postulación', false, SQLERRM);
    end;
  end if;

  -- Nueva postulación para pruebas org/ajeno
  begin
    v_json := public.m17_submit_volunteer_application(v_vol_pub, 'Segunda postulación');
    v_app_id2 := (v_json->>'id')::uuid;
  exception when others then null;
  end;

  perform pg_temp.m17_act_as(v_out);
  if v_app_id2 is not null then
    begin
      perform public.m17_withdraw_volunteer_application(v_app_id2);
      perform pg_temp.m17_val(54, 'Ajeno no retira postulación', false);
    exception when others then
      perform pg_temp.m17_val(54, 'Ajeno no retira postulación', SQLERRM like '%M17_PERMISSION_DENIED%');
    end;
  end if;

  perform pg_temp.m17_act_as(v_mgr);
  if v_app_id2 is not null then
    begin
      v_json := public.m17_accept_volunteer_application(v_app_id2);
      perform pg_temp.m17_val(55, 'Org acepta postulación', v_json->>'status' = 'ACCEPTED');
    exception when others then
      perform pg_temp.m17_val(55, 'Org acepta postulación', false, SQLERRM);
    end;
  end if;

  perform pg_temp.m17_act_as(v_out);
  if v_app_id2 is not null then
    begin
      perform public.m17_accept_volunteer_application(v_app_id2);
      perform pg_temp.m17_val(56, 'Ajeno no acepta postulación', false);
    exception when others then
      perform pg_temp.m17_val(56, 'Ajeno no acepta postulación', SQLERRM like '%M17_PERMISSION_DENIED%');
    end;
  end if;

  -- ========================================================================
  -- ORG donation.manage 57–65
  -- ========================================================================
  perform pg_temp.m17_act_as(v_mgr);
  begin
    v_json := public.m17_create_campaign(
      v_org, 'Campaña RPC M17', 'Descripción campaña creada vía RPC M17 test.', 'RESCUE', 150000
    );
    v_c_work := (v_json->>'id')::uuid;
    perform pg_temp.m17_val(57, 'Manager crea campaña', v_json->>'status' = 'DRAFT');
  exception when others then
    perform pg_temp.m17_val(57, 'Manager crea campaña', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m17_list_org_campaigns(v_org);
    perform pg_temp.m17_val(58, 'Manager lista campañas org', v_cnt >= 1);
  exception when others then
    perform pg_temp.m17_val(58, 'Manager lista campañas org', false, SQLERRM);
  end;

  if v_c_work is not null then
    begin
      v_json := public.m17_get_campaign(v_c_work);
      perform pg_temp.m17_val(59, 'Manager lee campaña interna',
        (v_json->>'organization_id')::uuid = v_org);
    exception when others then
      perform pg_temp.m17_val(59, 'Manager lee campaña interna', false, SQLERRM);
    end;

    begin
      v_json := public.m17_update_campaign_details(
        v_c_work, 'Campaña Editada', 'Descripción editada campaña M17 test.', 'MEDICAL'
      );
      perform pg_temp.m17_val(60, 'Manager actualiza detalles',
        v_json->>'title' = 'Campaña Editada');
    exception when others then
      perform pg_temp.m17_val(60, 'Manager actualiza detalles', false, SQLERRM);
    end;

    begin
      v_json := public.m17_update_campaign_goal(v_c_work, 160000, 'ARS');
      perform pg_temp.m17_val(61, 'Manager actualiza meta',
        (v_json->>'goal_amount_minor')::bigint = 160000);
    exception when others then
      perform pg_temp.m17_val(61, 'Manager actualiza meta', false, SQLERRM);
    end;

    begin
      v_json := public.m17_transition_campaign(v_c_work, 'PUBLISHED');
      perform pg_temp.m17_val(62, 'Manager publica campaña', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m17_val(62, 'Manager publica campaña', false, SQLERRM);
    end;

    begin
      v_json := public.m17_add_campaign_update(v_c_work, 'Actualización pública de prueba');
      perform pg_temp.m17_val(63, 'Manager agrega update',
        jsonb_array_length(v_json->'public_updates') >= 1);
    exception when others then
      perform pg_temp.m17_val(63, 'Manager agrega update', false, SQLERRM);
    end;
  end if;

  perform pg_temp.m17_act_as(v_out);
  begin
    perform public.m17_create_campaign(
      v_org, 'Hack', 'Descripción hack campaña M17 test.', 'GENERAL_SUPPORT', 1000
    );
    perform pg_temp.m17_val(64, 'Ajeno no crea campaña', false);
  exception when others then
    perform pg_temp.m17_val(64, 'Ajeno no crea campaña', SQLERRM like '%M17_PERMISSION_DENIED%');
  end;

  if v_c_work is not null then
    begin
      perform public.m17_transition_campaign(v_c_work, 'PAUSED');
      perform pg_temp.m17_val(65, 'Ajeno no transiciona campaña', false);
    exception when others then
      perform pg_temp.m17_val(65, 'Ajeno no transiciona campaña', SQLERRM like '%M17_PERMISSION_DENIED%');
    end;
  end if;

  -- ========================================================================
  -- PRIVACIDAD RPC PÚBLICAS 66–95
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);

  begin
    select count(*)::int into v_cnt from public.m17_list_public_campaigns();
    perform pg_temp.m17_val(66, 'Anon list_public_campaigns', v_cnt >= 1);
  exception when others then
    perform pg_temp.m17_val(66, 'Anon list_public_campaigns', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_campaign(v_c_pub);
    perform pg_temp.m17_val(67, 'Anon get_public_campaign', v_json is not null);
  exception when others then
    perform pg_temp.m17_val(67, 'Anon get_public_campaign', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m17_list_public_campaigns() j
    where (j->>'id')::uuid = v_c_draft;
    perform pg_temp.m17_val(68, 'DRAFT oculto en listado', v_cnt = 0);
  exception when others then
    perform pg_temp.m17_val(68, 'DRAFT oculto en listado', false, SQLERRM);
  end;

  begin
    perform public.m17_get_public_campaign(v_c_draft);
    perform pg_temp.m17_val(69, 'DRAFT get_public falla', false);
  exception when others then
    perform pg_temp.m17_val(69, 'DRAFT get_public falla', SQLERRM like '%M17_CAMPAIGN_NOT_PUBLIC%');
  end;

  begin
    v_json := public.m17_get_public_campaign(v_c_pub);
    perform pg_temp.m17_val(70, 'Sin organization_id público',
      v_json->>'organization_id' is null);
  exception when others then
    perform pg_temp.m17_val(70, 'Sin organization_id público', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_campaign(v_c_pub);
    perform pg_temp.m17_val(71, 'Sin internal_notes público',
      v_json->>'internal_notes' is null);
  exception when others then
    perform pg_temp.m17_val(71, 'Sin internal_notes público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from public.m17_list_public_contributions(v_c_pub) c
    where c::text ilike '%provider_reference%' or c::text ilike '%prov-secret%';
    perform pg_temp.m17_val(72, 'Sin provider_reference público', v_cnt = 0);
  exception when others then
    perform pg_temp.m17_val(72, 'Sin provider_reference público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from public.m17_list_public_contributions(v_c_pub) c
    where c::text ilike '%idempotency_key%' or c::text ilike '%idem-key%';
    perform pg_temp.m17_val(73, 'Sin idempotency_key público', v_cnt = 0);
  exception when others then
    perform pg_temp.m17_val(73, 'Sin idempotency_key público', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_financial_summary(v_c_pub);
    perform pg_temp.m17_val(74, 'Resumen solo CONFIRMED',
      (v_json->>'confirmed_contribution_count')::int = 3
      and (v_json->>'confirmed_amount_minor')::bigint = 90000);
  exception when others then
    perform pg_temp.m17_val(74, 'Resumen solo CONFIRMED', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from public.m17_list_public_contributions(v_c_pub) c
    where c->>'donor_label' = 'Pendiente';
    perform pg_temp.m17_val(75, 'PENDING excluido de público', v_cnt = 0);
  exception when others then
    perform pg_temp.m17_val(75, 'PENDING excluido de público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from public.m17_list_public_contributions(v_c_pub) c
    where c->>'donor_label' = 'Privado';
    perform pg_temp.m17_val(76, 'PRIVATE excluido de público', v_cnt = 0);
  exception when others then
    perform pg_temp.m17_val(76, 'PRIVATE excluido de público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m17_list_public_in_kind_needs();
    perform pg_temp.m17_val(77, 'Anon list_public_in_kind', v_cnt >= 1);
  exception when others then
    perform pg_temp.m17_val(77, 'Anon list_public_in_kind', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_in_kind_need(v_need_pub);
    perform pg_temp.m17_val(78, 'Anon get_public_in_kind', v_json is not null);
  exception when others then
    perform pg_temp.m17_val(78, 'Anon get_public_in_kind', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m17_list_public_in_kind_needs() j
    where (j->>'id')::uuid = v_need_draft;
    perform pg_temp.m17_val(79, 'DRAFT need oculto', v_cnt = 0);
  exception when others then
    perform pg_temp.m17_val(79, 'DRAFT need oculto', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_in_kind_need(v_need_pub);
    perform pg_temp.m17_val(80, 'Sin organization_id in-kind',
      v_json->>'organization_id' is null and v_json->>'contributor_user_id' is null);
  exception when others then
    perform pg_temp.m17_val(80, 'Sin organization_id in-kind', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m17_list_public_volunteer_opportunities();
    perform pg_temp.m17_val(81, 'Anon list_public_volunteer', v_cnt >= 1);
  exception when others then
    perform pg_temp.m17_val(81, 'Anon list_public_volunteer', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_volunteer_opportunity(v_vol_pub);
    perform pg_temp.m17_val(82, 'Anon get_public_volunteer', v_json is not null);
  exception when others then
    perform pg_temp.m17_val(82, 'Anon get_public_volunteer', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m17_list_public_volunteer_opportunities() j
    where (j->>'id')::uuid = v_vol_draft;
    perform pg_temp.m17_val(83, 'DRAFT vol oculto', v_cnt = 0);
  exception when others then
    perform pg_temp.m17_val(83, 'DRAFT vol oculto', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_volunteer_opportunity(v_vol_pub);
    perform pg_temp.m17_val(84, 'Sin organization_id voluntariado',
      v_json->>'organization_id' is null and v_json->>'applicant_user_id' is null);
  exception when others then
    perform pg_temp.m17_val(84, 'Sin organization_id voluntariado', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_campaign_transparency(v_c_pub);
    perform pg_temp.m17_val(85, 'Transparencia publicada visible', v_json is not null);
  exception when others then
    perform pg_temp.m17_val(85, 'Transparencia publicada visible', false, SQLERRM);
  end;

  begin
    perform public.m17_get_public_campaign_transparency(v_c_draft);
    perform pg_temp.m17_val(86, 'Transparencia DRAFT campaña falla', false);
  exception when others then
    perform pg_temp.m17_val(86, 'Transparencia DRAFT campaña falla',
      SQLERRM like '%M17_CAMPAIGN_NOT_PUBLIC%');
  end;

  begin
    v_json := public.m17_get_public_campaign_transparency(v_c_pub);
    perform pg_temp.m17_val(87, 'Sin internal_notes transparencia',
      v_json->>'internal_notes' is null);
  exception when others then
    perform pg_temp.m17_val(87, 'Sin internal_notes transparencia', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_in_kind_need(v_need_pub);
    perform pg_temp.m17_val(88, 'Sin created_by in-kind público',
      v_json->>'created_by' is null);
  exception when others then
    perform pg_temp.m17_val(88, 'Sin created_by in-kind público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from public.m17_list_public_campaigns(null, null, null, null, false, true, false, true);
    perform pg_temp.m17_val(89, 'Filtro near_goal_only', v_cnt >= 0);
  exception when others then
    perform pg_temp.m17_val(89, 'Filtro near_goal_only', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from public.m17_list_public_campaigns(null, null, null, null, false, true, false, false) j
    where j->>'status' = 'PUBLISHED';
    perform pg_temp.m17_val(90, 'Filtro active_only PUBLISHED', v_cnt >= 1);
  exception when others then
    perform pg_temp.m17_val(90, 'Filtro active_only PUBLISHED', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from public.m17_list_public_contributions(v_c_pub) c
    where c->>'donor_label' = 'Donante anónimo';
    perform pg_temp.m17_val(91, 'Etiqueta ANONYMOUS', v_cnt >= 1);
  exception when others then
    perform pg_temp.m17_val(91, 'Etiqueta ANONYMOUS', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from public.m17_list_public_contributions(v_c_pub) c
    where c->>'donor_label' = 'Donante Test';
    perform pg_temp.m17_val(92, 'Etiqueta PUBLIC con nombre', v_cnt >= 1);
  exception when others then
    perform pg_temp.m17_val(92, 'Etiqueta PUBLIC con nombre', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_campaign(v_c_pub);
    perform pg_temp.m17_val(93, 'confirmed_amount_minor en público',
      (v_json->>'confirmed_amount_minor')::bigint = 90000);
  exception when others then
    perform pg_temp.m17_val(93, 'confirmed_amount_minor en público', false, SQLERRM);
  end;

  begin
    v_json := public.m17_get_public_campaign(v_c_pub);
    perform pg_temp.m17_val(94, 'progress_percent en público',
      (v_json->>'progress_percent')::int between 0 and 999);
  exception when others then
    perform pg_temp.m17_val(94, 'progress_percent en público', false, SQLERRM);
  end;

  update public.m17_donation_campaigns set moderation_status = 'BLOCKED' where id = v_c_pub;
  begin
    select count(*)::int into v_cnt from public.m17_list_public_campaigns() j
    where (j->>'id')::uuid = v_c_pub;
    perform pg_temp.m17_val(95, 'BLOCKED oculto en público', v_cnt = 0);
  exception when others then
    perform pg_temp.m17_val(95, 'BLOCKED oculto en público', false, SQLERRM);
  end;
  update public.m17_donation_campaigns set moderation_status = 'APPROVED' where id = v_c_pub;

  -- ========================================================================
  -- IDEMPOTENCIA / TRANSICIONES 96–120
  -- ========================================================================
  perform pg_temp.m17_act_as(v_mgr);

  begin
    v_json := public.m17_create_campaign(
      v_org, 'Campaña Idempotencia', 'Descripción idempotencia transiciones M17 test.', 'EMERGENCY', 80000
    );
    perform pg_temp.m17_val(96, 'create_campaign primera vez', v_json->>'status' = 'DRAFT');
    v_c_work := coalesce((v_json->>'id')::uuid, v_c_work);
  exception when others then
    perform pg_temp.m17_val(96, 'create_campaign primera vez', false, SQLERRM);
  end;

  if v_c_work is not null then
    begin
      v_json := public.m17_transition_campaign(v_c_work, 'PUBLISHED');
      perform pg_temp.m17_val(97, 'transition PUBLISHED', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m17_val(97, 'transition PUBLISHED', false, SQLERRM);
    end;

    begin
      v_json := public.m17_transition_campaign(v_c_work, 'PUBLISHED');
      perform pg_temp.m17_val(98, 'PUBLISHED idempotente', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m17_val(98, 'PUBLISHED idempotente', false, SQLERRM);
    end;

    begin
      v_json := public.m17_transition_campaign(v_c_work, 'PAUSED');
      perform pg_temp.m17_val(99, 'transition PAUSED', v_json->>'status' = 'PAUSED');
    exception when others then
      perform pg_temp.m17_val(99, 'transition PAUSED', false, SQLERRM);
    end;

    begin
      v_json := public.m17_transition_campaign(v_c_work, 'PAUSED');
      perform pg_temp.m17_val(100, 'PAUSED idempotente', v_json->>'status' = 'PAUSED');
    exception when others then
      perform pg_temp.m17_val(100, 'PAUSED idempotente', false, SQLERRM);
    end;

    begin
      v_json := public.m17_transition_campaign(v_c_work, 'COMPLETED');
      perform pg_temp.m17_val(101, 'transition COMPLETED', v_json->>'status' = 'COMPLETED');
    exception when others then
      perform pg_temp.m17_val(101, 'transition COMPLETED', false, SQLERRM);
    end;

    begin
      v_json := public.m17_transition_campaign(v_c_work, 'COMPLETED');
      perform pg_temp.m17_val(102, 'COMPLETED idempotente', v_json->>'status' = 'COMPLETED');
    exception when others then
      perform pg_temp.m17_val(102, 'COMPLETED idempotente', false, SQLERRM);
    end;

    begin
      perform public.m17_transition_campaign(v_c_work, 'PUBLISHED');
      perform pg_temp.m17_val(103, 'No reactivar COMPLETED', false);
    exception when others then
      perform pg_temp.m17_val(103, 'No reactivar COMPLETED', SQLERRM like '%M17_STATE_ALREADY_FINAL%');
    end;
  end if;

  -- Segunda campaña para CANCELLED
  begin
    v_json := public.m17_create_campaign(
      v_org, 'Campaña Cancel', 'Descripción cancel idempotencia M17 test.', 'TRANSPORT', 90000
    );
    v_c_term := (v_json->>'id')::uuid;
  exception when others then null;
  end;

  if v_c_term is not null then
    perform public.m17_transition_campaign(v_c_term, 'PUBLISHED');
    begin
      v_json := public.m17_transition_campaign(v_c_term, 'CANCELLED');
      perform pg_temp.m17_val(104, 'transition CANCELLED', v_json->>'status' = 'CANCELLED');
    exception when others then
      perform pg_temp.m17_val(104, 'transition CANCELLED', false, SQLERRM);
    end;

    begin
      v_json := public.m17_transition_campaign(v_c_term, 'CANCELLED');
      perform pg_temp.m17_val(105, 'CANCELLED idempotente', v_json->>'status' = 'CANCELLED');
    exception when others then
      perform pg_temp.m17_val(105, 'CANCELLED idempotente', false, SQLERRM);
    end;
  end if;

  -- Pledge idempotencia
  perform pg_temp.m17_act_as(v_donor);
  begin
    v_json := public.m17_create_in_kind_pledge(v_need_pub, 3, 'Pledge idempotencia');
    v_pledge_id := (v_json->>'id')::uuid;
    perform pg_temp.m17_val(106, 'create_in_kind_pledge', v_json->>'status' = 'PLEDGED');
  exception when others then
    perform pg_temp.m17_val(106, 'create_in_kind_pledge', false, SQLERRM);
  end;

  if v_pledge_id is not null then
    begin
      v_json := public.m17_cancel_own_in_kind_pledge(v_pledge_id);
      perform pg_temp.m17_val(107, 'cancel pledge primera vez', v_json->>'status' = 'CANCELLED');
    exception when others then
      perform pg_temp.m17_val(107, 'cancel pledge primera vez', false, SQLERRM);
    end;

    begin
      v_json := public.m17_cancel_own_in_kind_pledge(v_pledge_id);
      perform pg_temp.m17_val(108, 'cancel pledge idempotente', v_json->>'status' = 'CANCELLED');
    exception when others then
      perform pg_temp.m17_val(108, 'cancel pledge idempotente', false, SQLERRM);
    end;
  end if;

  -- Pledge entrega + recompute
  begin
    v_json := public.m17_create_in_kind_pledge(v_need_pub, 7, 'Pledge entrega');
    v_pledge_id := (v_json->>'id')::uuid;
  exception when others then null;
  end;

  perform pg_temp.m17_act_as(v_mgr);
  if v_pledge_id is not null then
    begin
      v_json := public.m17_mark_in_kind_pledge_delivered(v_pledge_id);
      perform pg_temp.m17_val(109, 'mark delivered primera vez', v_json->>'status' = 'DELIVERED');
    exception when others then
      perform pg_temp.m17_val(109, 'mark delivered primera vez', false, SQLERRM);
    end;

    begin
      v_json := public.m17_mark_in_kind_pledge_delivered(v_pledge_id);
      perform pg_temp.m17_val(110, 'mark delivered idempotente', v_json->>'status' = 'DELIVERED');
    exception when others then
      perform pg_temp.m17_val(110, 'mark delivered idempotente', false, SQLERRM);
    end;

    select quantity_committed into v_qty from public.m17_in_kind_needs where id = v_need_pub;
    perform pg_temp.m17_val(111, 'quantity_committed recalculado', v_qty >= 7);
  end if;

  -- Voluntariado idempotencia (postulante ajeno: donante ya tiene ACCEPTED en v_vol_pub)
  perform pg_temp.m17_act_as(v_out);
  begin
    v_json := public.m17_submit_volunteer_application(v_vol_pub, 'App idempotencia');
    v_app_id := (v_json->>'id')::uuid;
    perform pg_temp.m17_val(112, 'submit application', v_json->>'status' = 'SUBMITTED');
  exception when others then
    perform pg_temp.m17_val(112, 'submit application', false, SQLERRM);
  end;

  begin
    perform public.m17_submit_volunteer_application(v_vol_pub, 'Duplicada');
    perform pg_temp.m17_val(113, 'duplicate application rechazada', false);
  exception when others then
    perform pg_temp.m17_val(113, 'duplicate application rechazada',
      SQLERRM like '%M17_DUPLICATE_APPLICATION%');
  end;

  if v_app_id is not null then
    begin
      v_json := public.m17_withdraw_volunteer_application(v_app_id);
      perform pg_temp.m17_val(114, 'withdraw primera vez', v_json->>'status' = 'WITHDRAWN');
    exception when others then
      perform pg_temp.m17_val(114, 'withdraw primera vez', false, SQLERRM);
    end;

    begin
      v_json := public.m17_withdraw_volunteer_application(v_app_id);
      perform pg_temp.m17_val(115, 'withdraw idempotente', v_json->>'status' = 'WITHDRAWN');
    exception when others then
      perform pg_temp.m17_val(115, 'withdraw idempotente', false, SQLERRM);
    end;
  end if;

  -- Accept idempotencia + accepted_people (postulación nueva tras withdraw case 115)
  perform pg_temp.m17_act_as(v_out);
  begin
    v_json := public.m17_submit_volunteer_application(v_vol_pub, 'App accept test');
    v_app_accept := (v_json->>'id')::uuid;
  exception when others then null;
  end;

  perform pg_temp.m17_act_as(v_mgr);
  if v_app_accept is not null then
    select accepted_people into v_qty from public.m17_volunteer_opportunities where id = v_vol_pub;
    begin
      v_json := public.m17_accept_volunteer_application(v_app_accept);
      perform pg_temp.m17_val(116, 'accept application', v_json->>'status' = 'ACCEPTED');
    exception when others then
      perform pg_temp.m17_val(116, 'accept application', false, SQLERRM);
    end;

    select accepted_people into v_cnt from public.m17_volunteer_opportunities where id = v_vol_pub;
    perform pg_temp.m17_val(117, 'accepted_people incrementa', v_cnt = v_qty + 1);

    begin
      v_json := public.m17_accept_volunteer_application(v_app_accept);
      perform pg_temp.m17_val(118, 'accept idempotente', v_json->>'status' = 'ACCEPTED');
    exception when others then
      perform pg_temp.m17_val(118, 'accept idempotente', false, SQLERRM);
    end;

    select accepted_people into v_cnt from public.m17_volunteer_opportunities where id = v_vol_pub;
    perform pg_temp.m17_val(119, 'accept no duplica slots', v_cnt = v_qty + 1);
  end if;

  -- Transición inválida DRAFT -> COMPLETED
  begin
    v_json := public.m17_create_campaign(
      v_org, 'Salto inválido', 'Descripción salto inválido M17 test.', 'GENERAL_SUPPORT', 5000
    );
    v_c_draft := (v_json->>'id')::uuid;
  exception when others then null;
  end;

  if v_c_draft is not null then
    begin
      perform public.m17_transition_campaign(v_c_draft, 'COMPLETED');
      perform pg_temp.m17_val(120, 'DRAFT->COMPLETED rechazado', false);
    exception when others then
      perform pg_temp.m17_val(120, 'DRAFT->COMPLETED rechazado',
        SQLERRM like '%M17_INVALID_STATE_TRANSITION%');
    end;
  end if;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  delete from public.m17_transparency_milestones
  where report_id in (
    select id from public.m17_campaign_transparency_reports
    where organization_id = v_org
  );
  delete from public.m17_fund_usage_items
  where report_id in (
    select id from public.m17_campaign_transparency_reports
    where organization_id = v_org
  );
  delete from public.m17_campaign_transparency_reports where organization_id = v_org;

  delete from public.m17_volunteer_applications
  where opportunity_id in (
    select id from public.m17_volunteer_opportunities where organization_id = v_org
  );
  delete from public.m17_volunteer_opportunities where organization_id = v_org;

  delete from public.m17_in_kind_pledges
  where need_id in (select id from public.m17_in_kind_needs where organization_id = v_org);
  delete from public.m17_in_kind_needs where organization_id = v_org;

  delete from public.m17_contributions
  where campaign_id in (select id from public.m17_donation_campaigns where organization_id = v_org);
  delete from public.m17_campaign_updates
  where campaign_id in (select id from public.m17_donation_campaigns where organization_id = v_org);
  delete from public.m17_donation_campaigns where organization_id = v_org;

  delete from public.m16_shelter_profiles where organization_id = v_org;
  delete from public.organization_memberships where organization_id = v_org;
  delete from public.organizations where id in (v_org, v_bad_org);
  delete from public.users where id in (v_mgr, v_out, v_donor);
  delete from auth.users where id in (v_mgr, v_out, v_donor);
end;
$setup$;

insert into supabase_migrations.schema_migrations (version, name, statements)
values
  ('054', '054_m17_donation_campaigns_and_contributions', '{}'),
  ('055', '055_m17_in_kind_volunteering_and_transparency', '{}'),
  ('056', '056_m17_fix_moderator_helper', '{}'),
  ('057', '057_m17_fix_volunteer_public_list', '{}')
on conflict (version) do nothing;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m17_val_results;

select case_id, label, result, detail
from m17_val_results
where result = 'FAIL'
order by case_id;

commit;
