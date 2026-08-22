-- =============================================================================
-- LeoVer / ComunidadApp
-- DEVELOPMENT ONLY — READ ONLY
-- Inventario previo al reset de datos de desarrollo.
--
-- NO borra nada.
-- NO es migración.
-- NO se ejecuta en CI/deploy.
-- NO asumir project ref. Confirmar DEV/STAGING antes de cualquier paso siguiente.
-- =============================================================================

select current_database() as database_name,
       current_user as db_user,
       inet_server_addr() as server_addr,
       now() at time zone 'utc' as utc_now;

-- Project ref no se infiere desde SQL. Completar a mano tras confirmar Dashboard.
select 'UNCONFIRMED' as target_project_ref_must_be_confirmed_outside_sql;

-- ---------------------------------------------------------------------------
-- Auth vs perfil vs roles de plataforma
-- ---------------------------------------------------------------------------
select
  (select count(*) from auth.users) as auth_users_total,
  (select count(*) from public.users) as public_users_total,
  (select count(*) from public.users u
     where exists (
       select 1
       from public.user_role_assignments a
       join public.platform_roles r on r.id = a.role_id
       where a.user_id = u.id
         and a.revoked_at is null
         and r.code in ('ADMIN', 'SUPERADMIN')
     )) as admin_or_superadmin_public_users,
  (select count(*) from public.users
     where coalesce(account_type, 'PERSON') <> 'PERSON') as users_with_legacy_account_type;

select
  r.code as platform_role,
  count(*) filter (where a.revoked_at is null) as active_assignments
from public.platform_roles r
left join public.user_role_assignments a on a.role_id = r.id
group by r.code
order by r.code;

select id, email, account_type, name, created_at
from public.users
order by created_at nulls last
limit 50;

-- ---------------------------------------------------------------------------
-- Conteos operativos (0 si la tabla no existe)
-- ---------------------------------------------------------------------------
create temporary table if not exists _leover_reset_counts (
  bucket text not null,
  table_name text not null,
  row_count bigint not null
) on commit preserve rows;

truncate _leover_reset_counts;

do $$
declare
  rec record;
begin
  for rec in
    select * from (values
      ('identity', 'users'),
      ('identity', 'user_privacy_settings'),
      ('identity', 'user_consents'),
      ('identity', 'user_status_history'),
      ('identity', 'role_assignment_history'),
      ('identity', 'account_deletion_requests'),
      ('identity', 'device_tokens'),
      ('org', 'organizations'),
      ('org', 'organization_memberships'),
      ('org', 'organization_invitations'),
      ('org', 'organization_branches'),
      ('org', 'organization_resource_links'),
      ('org', 'organization_status_history'),
      ('org', 'organization_audit_log'),
      ('org', 'organization_verification_reviews'),
      ('org', 'organization_verification_document_refs'),
      ('pets', 'pets'),
      ('pets', 'pet_responsibilities'),
      ('pets', 'pet_authorizations'),
      ('pets', 'pet_transfers'),
      ('pets', 'pet_status_history'),
      ('pets', 'pet_clinical_records'),
      ('pets', 'pet_passports'),
      ('pets', 'pet_passport_credentials'),
      ('pets', 'pet_passport_verification_requests'),
      ('pets', 'pet_passport_verification_decisions'),
      ('pets', 'pet_passport_status_history'),
      ('adoptions', 'adoptions'),
      ('adoptions', 'adoption_requests'),
      ('adoptions', 'adoption_applications'),
      ('adoptions', 'adoption_interviews'),
      ('adoptions', 'adoption_document_requirements'),
      ('adoptions', 'adoption_agreements'),
      ('adoptions', 'adoption_finalizations'),
      ('adoptions', 'adoption_followup_plans'),
      ('adoptions', 'adoption_followup_checks'),
      ('adoptions', 'adoption_matches'),
      ('lost_found', 'lost_found_posts'),
      ('lost_found', 'lost_found_sightings'),
      ('lost_found', 'lost_found_sighting_details'),
      ('lost_found', 'lost_found_match_candidates'),
      ('lost_found', 'lost_found_match_decisions'),
      ('lost_found', 'lost_found_match_status_history'),
      ('foster', 'foster_home_profiles'),
      ('foster', 'foster_care_requests'),
      ('foster', 'foster_placements'),
      ('foster', 'foster_expenses'),
      ('foster', 'foster_evolution_entries'),
      ('foster', 'foster_help_requests'),
      ('foster', 'foster_help_contributions'),
      ('foster', 'foster_homes'),
      ('foster', 'foster_requests'),
      ('social', 'posts'),
      ('social', 'post_likes'),
      ('social', 'post_comments'),
      ('social', 'post_saves'),
      ('social', 'm19_social_posts'),
      ('social', 'm19_post_comments'),
      ('social', 'm19_post_reactions'),
      ('social', 'friend_connections'),
      ('social', 'user_blocks'),
      ('social', 'user_badges'),
      ('chat', 'conversations'),
      ('chat', 'conversation_participants'),
      ('chat', 'messages'),
      ('chat', 'm20_conversations'),
      ('chat', 'm20_messages'),
      ('chat', 'm20_user_blocks'),
      ('chat', 'm20_participant_state'),
      ('shelter', 'shelters'),
      ('shelter', 'shelter_profiles'),
      ('shelter', 'shelter_pet_placements'),
      ('shelter', 'shelter_volunteer_assignments'),
      ('shelter', 'shelter_campaigns'),
      ('shelter', 'shelter_campaign_updates'),
      ('shelter', 'shelter_supply_requests'),
      ('shelter', 'shelter_supply_contributions'),
      ('shelter', 'shelter_emergencies'),
      ('shelter', 'shelter_events'),
      ('shelter', 'shelter_event_registrations'),
      ('shelter', 'm16_shelter_profiles'),
      ('shelter', 'm16_shelter_opening_periods'),
      ('shelter', 'm16_shelter_public_contacts'),
      ('shelter', 'm16_shelter_needs'),
      ('shelter', 'm16_shelter_verification_requests'),
      ('help', 'donation_campaigns'),
      ('help', 'community_events'),
      ('help', 'event_interests'),
      ('help', 'm17_donation_campaigns'),
      ('help', 'm17_campaign_updates'),
      ('help', 'm17_contributions'),
      ('help', 'm17_in_kind_needs'),
      ('help', 'm17_in_kind_pledges'),
      ('help', 'm17_volunteer_opportunities'),
      ('help', 'm17_volunteer_applications'),
      ('help', 'm17_campaign_transparency_reports'),
      ('help', 'm17_fund_usage_items'),
      ('help', 'm17_transparency_milestones'),
      ('help', 'm18_community_events'),
      ('help', 'm18_event_registrations'),
      ('help', 'm18_event_reminders'),
      ('services', 'service_profiles'),
      ('services', 'service_bookings'),
      ('services', 'service_reviews'),
      ('services', 'm22_service_providers'),
      ('services', 'm22_provider_branches'),
      ('services', 'm22_service_offerings'),
      ('services', 'm23_availability_rules'),
      ('services', 'm23_availability_exceptions'),
      ('services', 'm23_bookings'),
      ('services', 'm23_booking_history'),
      ('vet', 'veterinary_clinic_profiles'),
      ('vet', 'veterinary_professionals'),
      ('vet', 'veterinary_clinic_professionals'),
      ('vet', 'veterinary_professional_specialties'),
      ('vet', 'veterinary_services'),
      ('vet', 'veterinary_opening_hours'),
      ('vet', 'veterinary_schedule_settings'),
      ('vet', 'veterinary_availability_rules'),
      ('vet', 'veterinary_availability_exceptions'),
      ('vet', 'veterinary_appointments'),
      ('vet', 'veterinary_appointment_status_history'),
      ('vet', 'veterinary_patient_relationships'),
      ('vet', 'veterinary_professional_access_grants'),
      ('vet', 'veterinary_professional_cares'),
      ('vet', 'veterinary_vaccination_records'),
      ('vet', 'veterinary_professional_documents'),
      ('vet', 'veterinary_follow_ups'),
      ('vet', 'veterinary_passport_update_proposals'),
      ('vet', 'veterinary_export_requests'),
      ('commerce', 'shop_products'),
      ('commerce', 'payment_intents'),
      ('commerce', 'm25_shops'),
      ('commerce', 'm25_products'),
      ('commerce', 'm25_promotions'),
      ('commerce', 'm25_cart_items'),
      ('commerce', 'm25_orders'),
      ('commerce', 'm25_order_lines'),
      ('commerce', 'm25_returns'),
      ('commerce', 'm25_order_history'),
      ('commerce', 'm25_stock_movements'),
      ('commerce', 'm25_return_lines'),
      ('media', 'file_assets'),
      ('media', 'file_asset_versions'),
      ('media', 'file_asset_links'),
      ('media', 'file_upload_sessions'),
      ('media', 'file_access_audit'),
      ('admin_ops', 'moderation_cases'),
      ('admin_ops', 'support_tickets'),
      ('admin_ops', 'notifications'),
      ('admin_ops', 'notification_events'),
      ('admin_ops', 'notification_deliveries'),
      ('admin_ops', 'notification_outbox'),
      ('reputation', 'm21_reviews'),
      ('reputation', 'm21_verification_requests'),
      ('ai', 'm26_ai_jobs'),
      ('ai', 'm26_assistance_sessions')
    ) as t(bucket, table_name)
  loop
    if to_regclass(format('public.%I', rec.table_name)) is not null then
      execute format(
        'insert into _leover_reset_counts(bucket, table_name, row_count) select %L, %L, count(*) from public.%I',
        rec.bucket, rec.table_name, rec.table_name
      );
    end if;
  end loop;
end $$;

select bucket, table_name, row_count
from _leover_reset_counts
order by bucket, table_name;

select bucket, sum(row_count) as rows
from _leover_reset_counts
group by bucket
order by bucket;

-- ---------------------------------------------------------------------------
-- Catálogos que DEBEN sobrevivir
-- ---------------------------------------------------------------------------
select 'platform_roles' as catalog, count(*) as rows from public.platform_roles
union all select 'permissions', count(*) from public.permissions
union all select 'role_permissions', count(*) from public.role_permissions
union all select 'organization_roles', count(*) from public.organization_roles
union all select 'organization_permissions', count(*) from public.organization_permissions
union all select 'organization_role_permissions', count(*) from public.organization_role_permissions
union all select 'reserved_usernames', count(*) from public.reserved_usernames
union all select 'file_retention_policies', count(*) from public.file_retention_policies
union all select 'veterinary_care_type_catalog', count(*) from public.veterinary_care_type_catalog;

-- Historial de migraciones (schema de Supabase CLI)
select n.nspname as schema_name, c.relname as table_name
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where c.relkind = 'r'
  and (
    (n.nspname = 'supabase_migrations' and c.relname = 'schema_migrations')
    or (n.nspname = 'auth' and c.relname = 'schema_migrations')
  );

-- ---------------------------------------------------------------------------
-- Storage
-- ---------------------------------------------------------------------------
select b.id as bucket_id, b.public, b.file_size_limit,
       (select count(*) from storage.objects o where o.bucket_id = b.id) as objects
from storage.buckets b
order by b.id;

-- ---------------------------------------------------------------------------
-- Clasificación de auth (no distingue “prueba” vs “real” con seguridad)
-- ---------------------------------------------------------------------------
select
  'SAFE_TO_DELETE_TEST_USERS' as decision,
  'NO' as value,
  'No hay señal canónica de usuario de prueba. No borrar auth.users automáticamente.' as reason;
