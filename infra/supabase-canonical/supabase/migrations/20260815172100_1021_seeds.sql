-- LeoVer Canonical Baseline
-- Logical migration: 1021
-- Idempotent catalogs. No QA users/pets. Legal documents are DRAFT PRE-LAUNCH.

insert into public.platform_roles (code, description) values
  ('USER', 'Default platform role'),
  ('MODERATOR', 'Moderation'),
  ('ADMIN', 'Platform admin'),
  ('SUPERADMIN', 'Superadmin')
on conflict (code) do nothing;

insert into public.platform_permissions (code, description) values
  ('platform.moderate', 'Moderate content'),
  ('platform.admin', 'Administer platform')
on conflict (code) do nothing;

insert into public.platform_role_permissions (role_code, permission_code) values
  ('MODERATOR', 'platform.moderate'),
  ('ADMIN', 'platform.moderate'),
  ('ADMIN', 'platform.admin'),
  ('SUPERADMIN', 'platform.moderate'),
  ('SUPERADMIN', 'platform.admin')
on conflict do nothing;

insert into public.permission_codes (code, scope, description) values
  ('pet.view', 'PET', 'View pet'),
  ('pet.edit', 'PET', 'Edit pet'),
  ('vitacora.view', 'VITACORA', 'View VitaCora'),
  ('vitacora.manage', 'VITACORA', 'Manage VitaCora'),
  ('vitacora.share', 'VITACORA', 'Share VitaCora'),
  ('health.manage_declared', 'PET', 'Manage declared health'),
  ('services.authorize', 'PET', 'Authorize services'),
  ('privacy.manage', 'PET', 'Manage pet privacy'),
  ('responsibility.manage', 'PET', 'Manage responsibility'),
  ('org.view', 'ORG', 'View organization'),
  ('org.edit', 'ORG', 'Edit organization'),
  ('org.members.manage', 'ORG', 'Manage members'),
  ('org.pets.manage', 'ORG', 'Manage org pets')
on conflict (code) do nothing;

insert into public.age_capability_rules (
  action_code, min_age_band, requires_guardian_confirmation, requires_contextual_consent, notes
) values
  ('org.create', 'ADULT_18_PLUS', false, false, 'Create organization'),
  ('org.admin', 'ADULT_18_PLUS', false, false, 'Administer organization'),
  ('vitacora.grant_full_shareable', 'ADULT_18_PLUS', true, true, 'FULL_SHAREABLE'),
  ('pet.transfer_responsibility', 'ADULT_18_PLUS', true, true, 'Transfer pet'),
  ('commerce.activate', 'ADULT_18_PLUS', false, false, 'Commercial activity'),
  ('payment.contract', 'ADULT_18_PLUS', true, true, 'Contracts/payments'),
  ('location.precise_share', 'TEEN_16_17', true, true, 'Precise location'),
  ('publication.public_consent', 'TEEN_16_17', true, true, 'Public publication'),
  ('donation.economic', 'ADULT_18_PLUS', true, false, 'Economic donation')
on conflict (action_code) do nothing;

insert into public.species (code, name, sort_key) values
  ('DOG', 'Perro', 1),
  ('CAT', 'Gato', 2),
  ('OTHER', 'Otra', 99)
on conflict (code) do nothing;

insert into public.breeds (species_code, name, sort_key) values
  ('DOG', 'Mestizo', 1),
  ('DOG', 'Labrador', 2),
  ('CAT', 'Mestizo', 1),
  ('CAT', 'Siamés', 2)
on conflict (species_code, name) do nothing;

insert into public.service_categories (code, name, sort_key) values
  ('WALKING', 'Paseo', 1),
  ('TRAINING', 'Adiestramiento', 2),
  ('CARE', 'Cuidado', 3),
  ('BOARDING', 'Guardería', 4),
  ('TRANSPORT', 'Transporte', 5),
  ('VETERINARY', 'Veterinaria', 6)
on conflict (code) do nothing;

insert into public.care_event_types (code, name, sort_key) values
  ('FEEDING', 'Alimentación', 1),
  ('MEDICATION', 'Medicación', 2),
  ('ACTIVITY', 'Actividad', 3),
  ('REST', 'Descanso', 4),
  ('OBSERVATION', 'Observación', 5),
  ('PHOTO', 'Foto', 6)
on conflict (code) do nothing;

insert into public.moderation_reason_codes (code, name, sort_key) values
  ('SPAM', 'Spam', 1),
  ('ABUSE', 'Abuso', 2),
  ('INAPPROPRIATE', 'Inapropiado', 3),
  ('OTHER', 'Otro', 99)
on conflict (code) do nothing;

insert into public.legal_documents (type, version, locale, content_hash, status, consent_code)
select * from (values
  ('TERMS', '0.1-draft', 'es-AR', 'draft-terms-prelaunch', 'DRAFT', null::text),
  ('PRIVACY', '0.1-draft', 'es-AR', 'draft-privacy-prelaunch', 'DRAFT', null),
  ('COMMUNITY_RULES', '0.1-draft', 'es-AR', 'draft-community-prelaunch', 'DRAFT', null),
  ('CONTEXTUAL', '0.1-draft', 'es-AR', 'draft-vitacora-share', 'DRAFT', 'VITACORA_SHARE')
) as v(type, version, locale, content_hash, status, consent_code)
where not exists (
  select 1 from public.legal_documents d
  where d.type = v.type and d.version = v.version and d.locale = v.locale
    and coalesce(d.consent_code, '') = coalesce(v.consent_code, '')
);

insert into public.location_nodes (id, kind, parent_id, name, iso_code, sort_key) values
  ('loc-ar', 'COUNTRY', null, 'Argentina', 'AR', 1)
on conflict (id) do nothing;

insert into public.location_nodes (id, kind, parent_id, name, iso_code, sort_key) values
  ('loc-ar-prov-caba', 'PROVINCE', 'loc-ar', 'Ciudad Autónoma de Buenos Aires', 'AR-C', 1),
  ('loc-ar-prov-buenos-aires', 'PROVINCE', 'loc-ar', 'Buenos Aires', 'AR-B', 2),
  ('loc-ar-prov-catamarca', 'PROVINCE', 'loc-ar', 'Catamarca', 'AR-K', 3),
  ('loc-ar-prov-chaco', 'PROVINCE', 'loc-ar', 'Chaco', 'AR-H', 4),
  ('loc-ar-prov-chubut', 'PROVINCE', 'loc-ar', 'Chubut', 'AR-U', 5),
  ('loc-ar-prov-cordoba', 'PROVINCE', 'loc-ar', 'Córdoba', 'AR-X', 6),
  ('loc-ar-prov-corrientes', 'PROVINCE', 'loc-ar', 'Corrientes', 'AR-W', 7),
  ('loc-ar-prov-entre-rios', 'PROVINCE', 'loc-ar', 'Entre Ríos', 'AR-E', 8),
  ('loc-ar-prov-formosa', 'PROVINCE', 'loc-ar', 'Formosa', 'AR-P', 9),
  ('loc-ar-prov-jujuy', 'PROVINCE', 'loc-ar', 'Jujuy', 'AR-Y', 10),
  ('loc-ar-prov-la-pampa', 'PROVINCE', 'loc-ar', 'La Pampa', 'AR-L', 11),
  ('loc-ar-prov-la-rioja', 'PROVINCE', 'loc-ar', 'La Rioja', 'AR-F', 12),
  ('loc-ar-prov-mendoza', 'PROVINCE', 'loc-ar', 'Mendoza', 'AR-M', 13),
  ('loc-ar-prov-misiones', 'PROVINCE', 'loc-ar', 'Misiones', 'AR-N', 14),
  ('loc-ar-prov-neuquen', 'PROVINCE', 'loc-ar', 'Neuquén', 'AR-Q', 15),
  ('loc-ar-prov-rio-negro', 'PROVINCE', 'loc-ar', 'Río Negro', 'AR-R', 16),
  ('loc-ar-prov-salta', 'PROVINCE', 'loc-ar', 'Salta', 'AR-A', 17),
  ('loc-ar-prov-san-juan', 'PROVINCE', 'loc-ar', 'San Juan', 'AR-J', 18),
  ('loc-ar-prov-san-luis', 'PROVINCE', 'loc-ar', 'San Luis', 'AR-D', 19),
  ('loc-ar-prov-santa-cruz', 'PROVINCE', 'loc-ar', 'Santa Cruz', 'AR-Z', 20),
  ('loc-ar-prov-santa-fe', 'PROVINCE', 'loc-ar', 'Santa Fe', 'AR-S', 21),
  ('loc-ar-prov-santiago', 'PROVINCE', 'loc-ar', 'Santiago del Estero', 'AR-G', 22),
  ('loc-ar-prov-tierra-del-fuego', 'PROVINCE', 'loc-ar', 'Tierra del Fuego', 'AR-V', 23),
  ('loc-ar-prov-tucuman', 'PROVINCE', 'loc-ar', 'Tucumán', 'AR-T', 24)
on conflict (id) do nothing;

insert into public.location_nodes (id, kind, parent_id, name, sort_key) values
  ('loc-ar-loc-san-vicente', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'San Vicente', 1),
  ('loc-ar-loc-almirante-brown', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'Almirante Brown', 2),
  ('loc-ar-loc-la-plata', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'La Plata', 3),
  ('loc-ar-loc-lomas', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'Lomas de Zamora', 4),
  ('loc-ar-loc-quilmes', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'Quilmes', 5),
  ('loc-ar-loc-avellaneda', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'Avellaneda', 6),
  ('loc-ar-loc-adrogué', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'Adrogué', 7),
  ('loc-ar-loc-burzaco', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'Burzaco', 8),
  ('loc-ar-loc-glew', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'Glew', 9),
  ('loc-ar-loc-alejandro-korn', 'LOCALITY', 'loc-ar-prov-buenos-aires', 'Alejandro Korn', 10),
  ('loc-ar-loc-caba', 'LOCALITY', 'loc-ar-prov-caba', 'CABA', 1),
  ('loc-ar-loc-palermo', 'LOCALITY', 'loc-ar-prov-caba', 'Palermo', 2),
  ('loc-ar-loc-cordoba-cap', 'LOCALITY', 'loc-ar-prov-cordoba', 'Córdoba', 1),
  ('loc-ar-loc-rosario', 'LOCALITY', 'loc-ar-prov-santa-fe', 'Rosario', 1)
on conflict (id) do nothing;
