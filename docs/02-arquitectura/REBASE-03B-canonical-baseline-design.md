# REBASE-03B — Canonical Baseline Design

**Producto:** LeoVer
**Sociedad operadora prevista:** COMUNIDAPP S.A.S. (aún no constituida)
**Fecha:** 15 de agosto de 2026
**Rama:** `main`
**HEAD:** `32577b9917500fe5e4096129f422ba29da4228b2`
**Tipo:** DESIGN ONLY — READ / ANALYSIS / DOCS ONLY
**No ejecutado:** SQL, migraciones, nuevo Supabase, db push, reset, código, APK, commit, push.

**Gobierno de entrada:**

- `MASTER_V12_CANONICAL_READY_FOR_REBASE_03`
- `TEEN_GOVERNANCE_READY`
- `REBASE_03A_READY_FOR_REVIEW`
- `FINAL_GOVERNANCE_READY_FOR_REBASE_03B`

---

## 1. Executive Summary

REBASE-03B diseña el **nuevo baseline canónico** de LeoVer para un **Supabase Staging nuevo**. No lo implementa.

El backend actual (82 migraciones `001`–`082`, ~219 tablas `public.*`, drift remoto documentado) se conserva como **referencia/rollback temporal**. No se reescribe historia Git. No se rejuegan `001`–`082` en el proyecto nuevo.

El baseline nuevo **no** contiene como arquitectura: `AccountType`, `AppMode`, Passport canónico, `pets.owner_id` singular, custodia temporal dentro de `pets`, marketplace V1, autoridades duplicadas, signed URL como identidad de media, ActiveContext como seguridad, ni hard-delete ordinario de historia de negocio.

Nombre canónico de M14: **VitaCora**.

Políticas de producto (autoridad Master v1.2 / D01 v1.3; **no** REBASE-03A):

| Política | Estado canónico |
| --- | --- |
| `PRIVACY_ERASURE_POLICY` | `DEFINED_PRELAUNCH` |
| `LEGAL_MINOR_CONSENT_POLICY` | `DEFINED_PRELAUNCH` |
| `GUARDIAN_VERIFICATION_POLICY` | `DEFINED_FOR_V1_PRELAUNCH` |
| `MINOR_ADVERTISING_POLICY` | `NO_SPONSORED_ADS_UNDER_18_V1` |
| `FINAL_LEGAL_REVIEW_REQUIRED_BEFORE_PROD` | `YES` |

`DEFINED_PRELAUNCH` cierra el diseño técnico. No es texto legal publicado ni ley vigente.

**Veredicto de esta etapa:** `REBASE_03B_DESIGN_READY`
**Siguiente:** REBASE-03C (implementación SQL en proyecto nuevo), previa aprobación de este diseño.

---

## 2. Source Precedence

| Prioridad | Documento | Uso en 03B |
| --- | --- | --- |
| 1 — HIGHEST | `docs/00-maestro/LeoVer-Documento-Maestro-v1.2.md` | Decisiones de producto/arquitectura |
| 2 — MODULE | `docs/01-producto/D01-Modulos-y-Orden-v1.3.md` | Autoridad Mxx, transversales, no módulos nuevos |
| 3 — EVIDENCE | `docs/02-arquitectura/REBASE-03A-canonical-architecture-audit.md` | Inventario real, KEEP/REBUILD/REPLACE/DROP, drift |

**REBASE-03A flags legales `PENDING` están superados.** No se editó 03A. Este diseño ignora esos PENDING y usa Master/D01.

Master v1.2, D01 v1.3 y REBASE-03A **no se modifican** en esta tarea.

---

## 3. Canonical Domain Map

```text
auth.users ──1:1── persons (M01/M02)
                      │
                      ├── age_band (derived) + protection + assurance
                      ├── guardian_relationships (person↔person; ≠ M08)
                      ├── legal_consent_events / privacy_requests
                      ├── platform_role_assignments (USER|MOD|ADMIN|SUPERADMIN)
                      └── organization_memberships ── organizations (M03)
                                                        │
                                                        └── capabilities[]  (type ≠ authority)
                                                              one ActiveContext per org (UX only)

pets (M08) ── created_by_user_id (provenance only)
     │
     ├── pet_responsibility_links  PERSON* OWNER|AUTHORIZED  |  ORGANIZATION responsible
     ├── pet_permission_grants     catalog codes (not booleans)
     ├── pet_custody_records       projection; authority = foster/stay/transport
     ├── pet_declared_health       M08 declared SoT
     ├── veterinary_*              M28 professional SoT
     └── vitacora_profiles (M14)   composition + moments + grants + proposals + links
                                      │
                                      └── media_assets (M05)  ← all domains reference asset id

M20 conversations: PERSON↔PERSON | PERSON↔ORGANIZATION (entity inbox + actor_user_id)
M19 social posts ≠ vitacora_moments
M22 provider (PERSON|ORGANIZATION) → M23 booking → optional daycare stay
M29 placements on M19; UNDER_18 sponsored = NO
M25 cart/orders/checkout = OUT OF V1 BASELINE
```

Principios no negociables:

```text
IDENTITY = PERSON
ACCOUNT_TYPE = NOT CANONICAL
ACTIVE_CONTEXT = UX ONLY
PLATFORM_ROLE ≠ ORG MEMBERSHIP ≠ PET RESPONSIBILITY ≠ AGE PROTECTION
ORGANIZATION = ENTITY + capabilities
PET = PERSISTENT IDENTITY
CREATOR = PROVENANCE ONLY
OWNER* = 1..N persons  OR  one organization responsible
CUSTODY ≠ RESPONSIBILITY ≠ VITACORA GRANT
PET PERMISSION ≠ AGE CAPABILITY
GUARDIAN ≠ PET OWNER
VITACORA = COMPOSITION (no duplicate facts)
NO passport_*
NO AccountType.TEEN
UNDER_13_AUTONOMOUS_ACCOUNT = NO
DELETE BUSINESS = SOFT/ARCHIVE/REVOKE
PRIVACY ERASURE = SEPARATE WORKFLOW
CHECKOUT V1 = NO
COMMUNITY ESSENTIAL = FREE
```

---

## 4. Entity Inventory

Nombres físicos son **propuestos para 03C**. Equivalentes claros se aceptan si preservan autoridad y relaciones. No SQL en esta etapa.

**CANONICAL_ENTITY_COUNT = 109** (96 de dominio + 13 de soporte; detalle al cierre de esta sección)

| # | Entity | Group | SoT | Module | Lifecycle | Privacy class |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `persons` | AUTH/PERSON | profile 1:1 `auth.users` | M01/M02 | active/suspended/erasure | MIXED; `birth_date` PRIVATE |
| 2 | `person_privacy_settings` | AUTH/PERSON | defaults + overrides | M02 | per person | PRIVATE |
| 3 | `person_contact_controls` | AUTH/PERSON | DM/discovery rules | M02/M20 | per person | PRIVATE |
| 4 | `device_tokens` | AUTH/PERSON | push installs | M06 | rotate/revoke | PRIVATE |
| 5 | `friendships` | AUTH/PERSON | graph + status | M02 | invite/accept/block | PRIVATE |
| 6 | `notification_preferences` | AUTH/PERSON | operational prefs | M06 | update | PRIVATE |
| 7 | `platform_roles` | PLATFORM | USER/MOD/ADMIN/SUPERADMIN | M02 | catalog | INTERNAL |
| 8 | `platform_permissions` | PLATFORM | codes | M02 | catalog | INTERNAL |
| 9 | `platform_role_permissions` | PLATFORM | map | M02 | catalog | INTERNAL |
| 10 | `user_platform_role_assignments` | PLATFORM | assignments | M02 | grant/revoke | INTERNAL |
| 11 | `legal_documents` | LEGAL | type/version/hash | transversal | draft/effective/retired | PUBLIC_WHEN_PUBLISHED |
| 12 | `legal_consent_events` | LEGAL | who/for whom/what | transversal | append + withdrawal | PRIVATE/AUDIT |
| 13 | `privacy_requests` | LEGAL | ACCESS/RECTIFICATION/UPDATE/ERASURE | transversal | open→resolved | PRIVATE/AUDIT |
| 14 | `privacy_request_events` | LEGAL | status trail | transversal | append | PRIVATE/AUDIT |
| 15 | `guardian_relationships` | GUARDIAN | minor↔adult | M01/M02 | invite/accept/end | PRIVATE |
| 16 | `guardian_relationship_events` | GUARDIAN | audit of link | M01/M02 | append | PRIVATE/AUDIT |
| 17 | `organizations` | ORGANIZATION | entity | M03 | active/archived | MIXED |
| 18 | `organization_capabilities` | ORGANIZATION | SHELTER/NGO/VET/DAYCARE/PROVIDER/… | M03 | add/remove | INTERNAL |
| 19 | `organization_branches` | ORGANIZATION | optional sites | M03 | active/archived | MIXED |
| 20 | `organization_public_profiles` | ORGANIZATION | public org page | M16 | publish/hide | PUBLIC_REDACTED |
| 21 | `organization_memberships` | MEMBERSHIP | person∈org | M03 | invite/active/ended | INTERNAL |
| 22 | `organization_roles` | MEMBERSHIP | org-local roles | M03 | catalog/custom | INTERNAL |
| 23 | `organization_role_permissions` | MEMBERSHIP | org ACL | M03 | grant | INTERNAL |
| 24 | `organization_invitations` | MEMBERSHIP | invite lifecycle | M03 | pending/accepted/expired | PRIVATE |
| 25 | `pets` | PET | persistent identity | M08 | active/deceased/archived | MIXED |
| 26 | `pet_lifecycle_events` | PET | status history | M08 | append | INTERNAL |
| 27 | `pet_responsibility_links` | RESPONSIBILITY | OWNER/AUTHORIZED or ORG | M08 | active period | MIXED |
| 28 | `pet_responsibility_events` | RESPONSIBILITY | change history | M08 | append | INTERNAL |
| 29 | `pet_family_invitations` | RESPONSIBILITY | family invite | M08 | pending/accepted/revoked | PRIVATE |
| 30 | `permission_codes` | FAMILY/PERMISSIONS | `pet.*` / `vitacora.*` | M08/M14 | catalog | INTERNAL |
| 31 | `pet_permission_grants` | FAMILY/PERMISSIONS | subject↔code | M08 | grant/revoke | INTERNAL |
| 32 | `pet_custody_records` | CUSTODY | who cares now | M08 proj. | open/closed | MIXED |
| 33 | `pet_declared_health` | HEALTH | declared profile | M08 | update | SENSITIVE |
| 34 | `pet_allergies` | HEALTH | declared/pro | M08/M28 | active/ended | SENSITIVE |
| 35 | `pet_medications` | HEALTH | declared/pro | M08/M28 | active/ended | SENSITIVE |
| 36 | `pet_declared_vaccinations` | HEALTH | declared only | M08 | recorded | SENSITIVE |
| 37 | `pet_parasite_treatments` | HEALTH | deworm/antiparasitic | M08/M28 | recorded | SENSITIVE |
| 38 | `pet_conditions` | HEALTH | conditions | M08/M28 | active/resolved | SENSITIVE |
| 39 | `pet_weights` | HEALTH | weight series | M08/M28 | append | SENSITIVE |
| 40 | `pet_care_instructions` | HEALTH | feeding/care notes | M08 | update | SENSITIVE |
| 41 | `vitacora_profiles` | VITACORA | 1:1 pet composition | M14 | active | MIXED |
| 42 | `vitacora_moments` | VITACORA | personal moments | M14 | private default | PRIVATE |
| 43 | `vitacora_access_grants` | VITACORA | service grants | M14 | active/expired/revoked | INTERNAL |
| 44 | `vitacora_update_proposals` | VITACORA | envelope | M14 | pending/decided | INTERNAL |
| 45 | `vitacora_integration_links` | VITACORA | pointer to SoT | M14 | visible/hidden | MIXED |
| 46 | `media_assets` | MEDIA | stable file identity | **M05** | draft→ready→archived | MIXED |
| 47 | `media_asset_versions` | MEDIA | versions | M05 | append | INTERNAL |
| 48 | `media_asset_links` | MEDIA | domain↔asset | M05 | attach/detach | inherits |
| 49 | `social_posts` | SOCIAL | feed | M19 | publish/hide | MIXED |
| 50 | `social_comments` | SOCIAL | comments | M19 | hide | MIXED |
| 51 | `social_reactions` | SOCIAL | reactions | M19 | set/unset | MIXED |
| 52 | `social_stories` | SOCIAL | stories/reels | M19 | expire/hide | MIXED |
| 53 | `conversations` | MESSAGING | thread | M20 | open/archived | PRIVATE |
| 54 | `conversation_participants` | MESSAGING | person or org inbox | M20 | join/leave | PRIVATE |
| 55 | `messages` | MESSAGING | body + `actor_user_id` | M20 | hide | PRIVATE |
| 56 | `message_blocks` | MESSAGING | block graph | M20 | set/unset | PRIVATE |
| 57 | `lost_found_alerts` | LOST/FOUND | alert + public_code | legacy+M13 | open/resolved | MIXED; coords PROTECTED |
| 58 | `lost_found_sightings` | LOST/FOUND | sightings | M13 | recorded | MIXED |
| 59 | `lost_found_match_candidates` | LOST/FOUND | AI/manual match | M13/M26 | review | INTERNAL |
| 60 | `lost_found_match_reviews` | LOST/FOUND | human confirm | M13/M26 | decided | INTERNAL |
| 61 | `adoption_publications` | ADOPTION | listing + public_code | M09 | open/closed | MIXED |
| 62 | `adoption_applications` | ADOPTION | applications | M09 | review | PRIVATE |
| 63 | `adoption_interviews` | ADOPTION | interviews | M09 | scheduled | PRIVATE |
| 64 | `adoption_agreements` | ADOPTION | decision/handoff | M09 | signed | PRIVATE |
| 65 | `adoption_followups` | ADOPTION | post-adopt | M09 | open/closed | PRIVATE |
| 66 | `foster_profiles` | FOSTER | personal capability | M10/M15 | active | MIXED |
| 67 | `foster_placements` | FOSTER | placement → custody | M10 | open/closed | MIXED |
| 68 | `foster_expenses` | FOSTER | registrable costs | M10 | recorded | PRIVATE |
| 69 | `service_categories` | PROVIDERS | catalog | M22 | catalog | PUBLIC |
| 70 | `service_providers` | PROVIDERS | PERSON or ORG | M22 | active | MIXED |
| 71 | `service_offerings` | PROVIDERS | offerings | M22 | active | PUBLIC_REDACTED |
| 72 | `provider_coverage_areas` | PROVIDERS | coverage | M22 | update | MIXED |
| 73 | `availability_slots` | BOOKINGS | slots + TZ | M23 | open/booked | MIXED |
| 74 | `bookings` | BOOKINGS | reservation | M23 | lifecycle | MIXED |
| 75 | `booking_participants` | BOOKINGS | people/orgs | M23 | attached | PRIVATE |
| 76 | `booking_instruction_snapshots` | BOOKINGS | agreed snapshot | M23 | immutable after confirm | SENSITIVE |
| 77 | `daycare_stays` | DAYCARE | stay from booking | M22/M23 | check-in/out | MIXED |
| 78 | `daycare_care_events` | DAYCARE | feed/meds/activity | M23 | recorded | PRIVATE |
| 79 | `daycare_incidents` | DAYCARE | incidents | M23 | recorded | SENSITIVE |
| 80 | `daycare_public_consents` | DAYCARE | public media consent | M23 | default NO / revoke | INTERNAL |
| 81 | `professional_profiles` | VETERINARY | vet person | M28 | active | MIXED |
| 82 | `clinic_affiliations` | VETERINARY | vet∈clinic | M28 | active/ended | INTERNAL |
| 83 | `veterinary_care_records` | VETERINARY | professional care | M28 | recorded | SENSITIVE |
| 84 | `veterinary_vaccination_records` | VETERINARY | professional vaccines | M28 | recorded | SENSITIVE |
| 85 | `veterinary_documents` | VETERINARY | attachments | M28 | recorded | SENSITIVE |
| 86 | `reviews` | REPUTATION | post-interaction | M21 | publish/hide | MIXED |
| 87 | `review_disputes` | REPUTATION | disputes | M21 | open/closed | PRIVATE |
| 88 | `actor_verifications` | REPUTATION | org/pro verify | M21 | granted/revoked | MIXED |
| 89 | `community_events` | EVENTS | events | M18 | publish | PUBLIC_REDACTED |
| 90 | `event_registrations` | EVENTS | attendance | M18 | register | PRIVATE |
| 91 | `donation_campaigns` | DONATIONS | campaign | M17 | open/closed | PUBLIC_REDACTED |
| 92 | `donation_contributions` | DONATIONS | direct-transfer evidence | M17 | recorded | PRIVATE |
| 93 | `in_kind_offers` | DONATIONS | goods | M17 | open | MIXED |
| 94 | `volunteer_offers` | DONATIONS | time | M17 | open | MIXED |
| 95 | `content_reports` | MODERATION | report | M04 | open | PRIVATE |
| 96 | `moderation_cases` | MODERATION | case | M04 | open/closed | INTERNAL |

Entidades de soporte contadas en catálogos/audit (incluidas en el total de diseño operativo, no como dominio de negocio extra):

| # | Entity | Group | Module |
| --- | --- | --- | --- |
| 97 | `location_nodes` | ADMIN | transversal |
| 98 | `species` | ADMIN | M08 |
| 99 | `breeds` | ADMIN | M08 |
| 100 | `age_capability_rules` | ADMIN | M01/M14 |
| 101 | `care_event_types` | ADMIN | M23 |
| 102 | `moderation_reason_codes` | ADMIN | M04 |
| 103 | `security_audit_events` | AUDIT | M07 |
| 104 | `tutorial_progress` | ADMIN | transversal |
| 105 | `notifications` | ADMIN | M06 |
| 106 | `notification_outbox` | ADMIN | M06 |
| 107 | `support_tickets` | ADMIN | M04 |
| 108 | `brand_campaigns` | BRAND STUDIO | M29 |
| 109 | `brand_placements` | BRAND STUDIO | M29 |

**Inventario operativo total = 109 entities** (96 de dominio + 13 de soporte).
**CANONICAL_ENTITY_COUNT = 109**

`daycare_guests` **no** es tabla separada: el huésped es `daycare_stays.pet_id` + custody.
`organization_permissions` se materializa como catálogo reutilizado (`permission_codes` con scope ORG) + `organization_role_permissions`.
M25 cart/orders/`payment_intents` **ausentes**.

---

## 5. Relationship Model

**CANONICAL_RELATION_COUNT = 52**

| ID | From | To | Cardinality | Rule |
| --- | --- | --- | --- | --- |
| R01 | `auth.users` | `persons` | 1:1 | same `user_id`; no second human identity |
| R02 | `persons` | `guardian_relationships` | 1:N as minor or adult | bilateral; ≠ pet |
| R03 | `persons` | `user_platform_role_assignments` | 1:N | platform only |
| R04 | `persons` | `organization_memberships` | 1:N | capabilities via org |
| R05 | `organizations` | `organization_capabilities` | 1:N | type ≠ authority |
| R06 | `organizations` | `organization_branches` | 1:N optional | not a second identity |
| R07 | `organizations` | `organization_memberships` | 1:N | lifecycle |
| R08 | `persons` | `pets.created_by_user_id` | 1:N | provenance immutable |
| R09 | `pets` | `pet_responsibility_links` | 1:N | many PERSON OWNER; ≤1 ORG responsible |
| R10 | `persons` | `pet_responsibility_links` | 1:N | OWNER/AUTHORIZED |
| R11 | `organizations` | `pet_responsibility_links` | 1:N | institutional responsible |
| R12 | `pet_responsibility_links` | `pet_permission_grants` | 1:N | catalog codes |
| R13 | `pets` | `pet_custody_records` | 1:N | ≤1 ACTIVE |
| R14 | `foster_placements` | `pet_custody_records` | 1:1 origin | domain authority |
| R15 | `daycare_stays` | `pet_custody_records` | 1:1 origin | domain authority |
| R16 | `pets` | `vitacora_profiles` | 1:1 | composition header |
| R17 | `vitacora_profiles` | `vitacora_moments` | 1:N | private default |
| R18 | `pets` | `vitacora_access_grants` | 1:N | person or org grantee |
| R19 | `vitacora_access_grants` | `persons` / `organizations` | N:1 | exclusive holder kind |
| R20 | `vitacora_update_proposals` | authoritative domain row | N:1 after ACCEPT | no copy |
| R21 | `vitacora_integration_links` | source record | N:1 | hide ≠ destroy |
| R22 | `pets` | `pet_declared_*` | 1:N | declared SoT |
| R23 | `pets` | `veterinary_*` | 1:N | professional SoT |
| R24 | domains | `media_assets` | N:1 via links | no signed URL identity |
| R25 | `media_assets` | `auth.users` created_by | N:1 | provenance |
| R26 | `conversations` | `conversation_participants` | 1:2..N | person or org |
| R27 | `messages` | `persons` actor | N:1 | always set on institutional |
| R28 | `bookings` | `pets` | N:1 | identity ≠ grant |
| R29 | `bookings` | `vitacora_access_grants` | N:0..1 | nullable |
| R30 | `bookings` | `booking_instruction_snapshots` | 1:1 | agreed facts |
| R31 | `bookings` | `daycare_stays` | 1:0..1 | specialized flow |
| R32 | `daycare_stays` | `daycare_public_consents` | 1:0..1 | DEFAULT NO |
| R33 | `daycare_stays` | `vitacora_integration_links` | 1:0..1 | optional save |
| R34 | `service_providers` | `persons` XOR `organizations` | N:1 | one holder |
| R35 | `service_providers` | `service_offerings` | 1:N | categories |
| R36 | `professional_profiles` | `persons` | N:1 | person ≠ professional |
| R37 | `clinic_affiliations` | `professional_profiles` × `organizations` | N:N | multi-clinic |
| R38 | `reviews` | booking/stay/relationship | N:1 | eligible interaction |
| R39 | `lost_found_alerts` | `pets` optional | N:0..1 | public_code own |
| R40 | `adoption_publications` | `pets` | N:1 | same pet id forever |
| R41 | `foster_profiles` | `persons` | 1:1 | not an org by default |
| R42 | `legal_consent_events` | `legal_documents` | N:1 | versioned |
| R43 | `legal_consent_events` | subject + actor persons | N:1+1 | self vs adult-for-teen |
| R44 | `privacy_requests` | subject person | N:1 | erasure ≠ product delete |
| R45 | `tutorial_progress` | `persons` | N:1 per key | independent |
| R46 | `brand_placements` | `social_posts` | N:1 | no 2nd network |
| R47 | `location_nodes` | parent node | tree | country→province→locality |
| R48 | `pets` | `location_nodes` | N:0..1 | no free-text authority |
| R49 | `persons` | `location_nodes` | N:0..1 | approximate public |
| R50 | `age_capability_rules` | action codes | catalog | evaluated with ACL |
| R51 | `content_reports` | target polymorphic | N:1 | moderation |
| R52 | `security_audit_events` | actor + entity | append | central security only |

Holder XOR pattern (reused): `holder_kind` + `holder_person_id` + `holder_organization_id` with CHECK exactly one set.

---

## 6. Authority Matrix

| Fact | Authority | VitaCora role | Forbidden |
| --- | --- | --- | --- |
| Human identity | M01 `persons` + `auth.users` | — | `account_type`, second user |
| Age/protection | derived from `persons.birth_date` | — | stored stale age; JWT as sole ACL |
| Guardian | `guardian_relationships` | — | auto pet OWNER / auto DM read |
| Platform admin | M02 assignments | — | org role as platform admin |
| Organization | M03 | — | `organizations.type` as ACL |
| ActiveContext | client UX | — | RLS/JWT |
| Pet identity | M08 `pets` | integrates | Passport snapshot |
| Pet responsibility | M08 links | — | `pets.owner_id` |
| Family permissions | M08 grants | codes shared with M14 | unbounded `text[]` as only ACL |
| Custody | origin domain + projection | optional link | custody columns on `pets` |
| Declared health | M08 declared tables | integrates | `vitacora_vaccinations` copy |
| Professional health | M28 | integrates | copy into M14 on ACCEPT |
| Moments | M14 | owns | auto social post |
| Grants / proposals / integration | M14 | owns | visibility enum as grant |
| Media identity | **M05 `media_assets`** | refs | persist signed URL |
| Social | M19 | ≠ moments | second M29 network |
| Messaging | M20 | — | employee-owned org thread |
| Lost/found | alerts + M13 | public ≠ grant | public lat/lng |
| Adoption | M09 | same `pets.id` | archive identity on adopt |
| Foster | M10/M15 | custody | default org |
| Provider | M22 | — | one table per category |
| Booking/stay | M23 | snapshot + optional link | booking = grant |
| Reputation | M21 | — | arbitrary reviews |
| Donations | M17 | — | LeoVer custody of funds |
| Ads | M29 on M19 | — | UNDER_18 sponsored |
| Marketplace | — | — | cart/orders/checkout V1 |
| Legal events | transversal | — | `accepted=true` global |
| Product delete | domain flags | hide integration | hard-delete history |
| Privacy erasure | `privacy_requests` | minimize links | destroy third-party SoT |

---

## 7. Person / Teen / Guardian

### 7.1 `persons`

| Field (logical) | Notes |
| --- | --- |
| `user_id` PK = `auth.users.id` | same identity forever |
| `username` UNIQUE | required at signup |
| `display_name` | |
| `avatar_asset_id` | M05; nullable |
| `birth_date` `date` | **PRIVATE**; needed to derive band |
| `age_band` | **generated/derived**, never source of truth |
| `protection_state` | derived + overrides (e.g. locked) |
| `age_assurance` | SELF_DECLARED / ACCOUNT_CONFIRMED / DOCUMENT_VERIFIED |
| `lifecycle_status` | ACTIVE / SUSPENDED / PENDING_ERASURE / ERASED_MINIMIZED |
| `privacy_state` | public/private defaults |
| `email_verified_at` | from auth or mirror |
| — | **no** `account_type` |
| — | **no** stored `age_years` |

Age band (product, not law):

| Band | Rule (derived) | Account |
| --- | --- | --- |
| `UNDER_13` | age < 13 | **no autonomous account** |
| `TEEN_13_15` | 13–15 | teen + guardian required |
| `TEEN_16_17` | 16–17 | teen, more autonomy, still protected |
| `ADULT_18_PLUS` | ≥ 18 | adult |

Recompute on read for capability checks (function `person_age_band(birth_date, as_of date)`). Optional stored generated column for index, invalidated by date change — never a chosen role.

At 18: **same** `user_id`, pets, VitaCora, history, memberships. Only band/protection/capabilities change.

### 7.2 Age assurance (extensible)

V1: **no DNI / selfie / tutela docs** by default.

| State | Meaning |
| --- | --- |
| `SELF_DECLARED` | birth_date provided by user |
| `ACCOUNT_CONFIRMED` | adult account verified + bilateral guardian accept (teen V1) |
| `DOCUMENT_VERIFIED` | reserved; not required to create teen account |

Physical column names not frozen. Model must accept a stronger method later without replacing the person.

### 7.3 Guardian V1 — `DEFINED_FOR_V1_PRELAUNCH`

Adult must be: PERSON LeoVer, `ADULT_18_PLUS`, lifecycle ACTIVE, email/account verified, bilateral accept.

Flows: teen invites adult → adult signs in and accepts; or adult invites teen → teen confirms.

Logical columns: `minor_user_id`, `adult_user_id`, `requested_by`, `status`, `accepted_at`, `ended_at`, `verification_method`, `created_by_actor`, timestamps.

UX label: **“Adulto responsable”**. Never “tutor legal verificado” unless legally verified (V1 does not).

**Does not grant:** pet ownership, VitaCora, messages, content, contacts, private history.

Grants only expressly defined protection / authorization / consent / safety functions.

### 7.4 Age capability

```text
allowed =
  authenticated actor
  AND domain permission
  AND age_capability_rules(action, age_band)
  AND contextual consent/policy if required
```

`age_capability_rules` is a catalog (admin/legal-updated), **not** hardcoded law in CHECK constraints beyond UNDER_13 signup deny.

Examples of actions that **must** consult the catalog: `vitacora.grant_full_shareable`, `pet.transfer_responsibility`, `org.create`, `org.admin`, `commerce.activate`, `payment.contract`, `privacy.relax_teen_defaults`, `location.precise_share`, `publication.public_consent`, `donation.economic`.

Exact 13–15 vs 16–17 matrix: product schema ready; **final legal review before production**.

UI never grants.

### 7.5 Minor consent events

`legal_consent_events` must distinguish:

- self acceptance (adult)
- `MINOR_ASSENT`
- `GUARDIAN_CONSENT` (adult actor, teen subject)
- contextual consents
- withdrawal

13–15: teen assent + adult confirmation for create/activate (per prelaunch policy).
16–17: greater operational autonomy; adult confirmation for legal/economic/especially sensitive actions when the catalog says so.

---

## 8. Organization

- One `organizations` row = one entity = **one** ActiveContext item (“Mundo Mascota”).
- `organization_capabilities`: SHELTER, NGO, VETERINARY_CLINIC, DAYCARE, PROVIDER, OTHER.
- `organizations.primary_label` may exist for UX; **not** ACL and **not** a single-capability lock.
- Memberships: invite → accept → active → ended. Roles + permission catalog.
- Members operate institutional pets via membership + org permission. They are **not** individual pet OWNERs.
- Branches: justified by current product (`OrganizationBranchesScreen`). Sites, not identities. Optional.
- Essential shelter/NGO tools: **no paid entitlement** (Compromiso Comunidad).

ActiveContext = `(PERSONAL | ORGANIZATION:{org_id})`. Never `ORGANIZATION:{org_id}:VET` as a separate identity.

---

## 9. Pet / Family / Responsibility

### 9.1 `pets`

No `owner_id`. No `age_years` as SoT.

| Logical | Rule |
| --- | --- |
| `created_by_user_id` | immutable provenance |
| `lifecycle_status` | ACTIVE / DECEASED / ARCHIVED |
| `avatar_asset_id` | M05 |
| `public_code` | unique; lives on `pets` (public pages). VitaCora does not own a second code |
| `home_locality_id` | `location_nodes` |
| birth model | see 9.2 |

### 9.2 Pet birth precision

| `birth_precision` | Persist | Display examples |
| --- | --- | --- |
| `EXACT_DATE` | `birth_date` | 3 años y 4 meses |
| `MONTH_PRECISION` | `birth_year` + `birth_month` | 3 años y 4 meses (approx month) |
| `YEAR_PRECISION` | `birth_year` | 5 años |
| `ESTIMATED` | `estimated_age_months` + `estimated_as_of` | Aprox. 2 años |
| `UNKNOWN` | none | Edad desconocida |

Never invent an exact `birth_date` from estimate. Age display is derived at read time.

### 9.3 Responsibility

`pet_responsibility_links`:

- `holder_kind` PERSON | ORGANIZATION
- PERSON roles: `OWNER` | `AUTHORIZED` (multiple OWNER allowed)
- ORGANIZATION: `RESPONSIBLE` (at most one active org responsible)
- `status`, `valid_from`, `valid_until`, `granted_by_actor_user_id`, `granted_by_entity`
- XOR holder CHECK

Example: created_by=Verónica; OWNER=Verónica+Leo; AUTHORIZED=Carolina.

Org example: responsible=Refugio Patitas; created_by=Juan (actor only).

History: `pet_responsibility_events` append-only. Same `pets.id` and same VitaCora across refuge→family. **Do not** auto-transfer internal notes, DMs, secrets, private third-party content.

### 9.4 Family permissions

Catalog `permission_codes`, rows in `pet_permission_grants`. Not boolean columns.

Minimum codes: `pet.view`, `pet.edit`, `vitacora.view`, `vitacora.manage`, `vitacora.share`, `health.manage_declared`, `services.authorize`, `privacy.manage`, `responsibility.manage`.

Extensible via catalog insert. OWNER may have equivalent capability sets; authority is the grant set, not the role name alone.

Invitations: `pet_family_invitations` (role, proposed grants, expires, accept/revoke).

Teen may be OWNER/AUTHORIZED. Age capability still applies.

---

## 10. VitaCora

M14 = **composition + history + visibility + access + integration**. Not a mega-table. Not a second SoT.

### 10.1 M14-owned entities

| Entity | Owns |
| --- | --- |
| `vitacora_profiles` | 1:1 settings, visibility, public presentation flags |
| `vitacora_moments` | personal moments |
| `vitacora_access_grants` | service sharing |
| `vitacora_update_proposals` | common envelope |
| `vitacora_integration_links` | pointers + hide/show |

### 10.2 Not M14-owned (integrated only)

Identity/responsibility (M08), declared health (M08), professional health (M28), foster (M10), stays (M23), adoption (M09), media bytes (M05).

**Forbidden:** `vaccinations` + `vitacora_vaccinations`.

### 10.3 Moments

Kinds: arrival/home, birthday, memories, photos, trips, milestones, personal notes.

`visibility` default `PRIVATE`. Moment ≠ `social_posts`. Sharing to social is a **new** M19 action referencing the moment; not the same row.

### 10.4 Grants

Scopes: `ESSENTIAL` | `HEALTH` | `ESSENTIAL_AND_HEALTH` | `FULL_SHAREABLE`.
`NONE` = **no active grant row** (absence). Revocation keeps the row with `revoked_at` for audit.

Recommended duration model (no extra enum required):

- `expires_at` null = indeterminate
- `expires_at` set = until date
- `revoked_at` set = quitar acceso

Logical fields: `pet_id`, grantee XOR person/org, `purpose`, `scope`, `granted_by_actor_user_id`, `granted_at`, `expires_at`, `revoked_at`, `revoked_by`.

ESSENTIAL: identification, photo, species, breed, sex, age display, size, basic functional data.
HEALTH: allergies, medication, vaccines, relevant conditions, deworming, antiparasitic, necessary health.
FULL_SHAREABLE: all **functionally shareable** data. **Excludes** private moments, DMs, audit, secrets, technical IDs, private third-party data, exact protected location.

Org grantee: members read via membership + org `vitacora.view`. No per-employee grant.

FULL_SHAREABLE also requires age capability (+ guardian confirm if catalog says so).

### 10.5 Public pet page

`/mascota/{publicCode}` uses `pets.public_code` + redacted projection. **≠** VitaCora grant. Safe 404. No internal ids or precise coords.

---

## 11. Health

| Data | SoT | Notes |
| --- | --- | --- |
| Allergies | `pet_allergies` + `source` | declared or professional |
| Medication | `pet_medications` | same |
| Vaccines declared | `pet_declared_vaccinations` | M08 |
| Vaccines professional | `veterinary_vaccination_records` | M28; VitaCora points |
| Deworm / antiparasitic | `pet_parasite_treatments` | `kind` + source |
| Weight | `pet_weights` | series |
| Conditions | `pet_conditions` | |
| Professional care | `veterinary_care_records` | M28 |
| Care instructions | `pet_care_instructions` | declared; snapshot into booking |

Passport legacy fields (name/species/sex/microchip/birth on snapshot, copied vaccines) **do not** return. Rehome to M08/M28.

`provenance` on each health fact: `DECLARED | PROFESSIONAL | THIRD_PARTY | VERIFIED | INFERRED | SYSTEM` + actor/org/professional/source/accepted_by/at.

---

## 12. Proposals / Provenance

Common envelope `vitacora_update_proposals`:

- `origin_kind`: VET | WALKER | TRAINER | CAREGIVER | DAYCARE | TRANSPORT | FOSTER | OTHER
- `payload` structured (not opaque blob as only truth)
- `source_record_id` / `source_table`
- `status`: PENDING | ACCEPTED | REJECTED | CORRECTION_REQUESTED | CANCELLED
- actor, organization, `professional_profile_id`, timestamps

ACCEPT → write **authoritative domain** → create/update `vitacora_integration_links`.
No third-party direct edit of VitaCora or health SoT.

Provenance is enum + structured actors. Free-text is optional note, never the only provenance.

---

## 13. Custody

`pet_custody_records` is a **projection** with mandatory `source_domain` + `source_record_id`.

Fields: pet, custodian person XOR org, purpose, `starts_at`, `ends_at`, status (ACTIVE/ENDED/CANCELLED).

At most one ACTIVE custody per pet (product rule; enforce in RPC).

Foster/transit: personal capability (`foster_profiles` on PERSON). Opens/closes custody. Original responsibility remains unless explicit transfer.

Daycare stay and transport likewise. Do not store TEMPORARY_CUSTODIAN on the responsibility table as the authority.

---

## 14. Services / Booking / Daycare

### 14.1 Providers (M22)

One `service_providers` for PERSON or ORGANIZATION. Multiple `service_offerings` / categories. Specialize tables only when workflow differs (daycare stay is the specialization).

### 14.2 Bookings (M23)

`bookings`: offering, pet, participants, status, `starts_at` timestamptz, `zone_id` IANA, optional `vitacora_grant_id`, lifecycle.

`booking_instruction_snapshots`: feeding, indicated meds, public consent ref, agreed scope, emergency contact, specials. Immutable after confirmation. **Not** a VitaCora copy.

Booking-required info (which pet) exists even when grant is absent.

M12 clinic appointments: **do not** keep a second agenda authority. Clinic profile (M12/M03 capability) + offerings; slots/bookings are M23 with category VETERINARY.

### 14.3 Daycare

```text
reservation → check-in → stay → custody ACTIVE → care events / incidents → check-out
```

Care events (`care_event_types`): feeding, medication administration, activity, rest, observation, photo. **Not** auto-integrated into VitaCora.

Three independent decisions:

| Decision | Default | Entity |
| --- | --- | --- |
| A operational private access | staff via membership | stay + custody |
| B public visibility/media | **NO** | `daycare_public_consents` revocable; public posts must FK the consent |
| C save stay in VitaCora | optional | `vitacora_integration_links` → stay |

“Eliminar de VitaCora” hides the integration. Stay remains.

---

## 15. Veterinary

Separate: PERSON, `professional_profiles`, clinic ORGANIZATION, `service_providers` representation.

A vet may have many `clinic_affiliations`. Clinic records remain if the professional leaves.

Every professional act stores: `actor_user_id`, `professional_profile_id`, `organization_id`, provenance.

Admin non-vet **cannot** sign a professional act (RPC deny).

M28 ACCEPT never copies vaccines into a VitaCora-owned vaccination table.

---

## 16. Messaging / Social

### 16.1 M20

`conversations.subject_kind`: PERSON | ORGANIZATION | PLATFORM_SUPPORT.

Participants: person and/or **organization inbox**. Thread belongs to the organization. Each institutional message has `actor_user_id`. Membership end ≠ thread end.

Person↔person DMs remain.

Teen controls (`person_contact_controls` + M20):

- restrict unknown DMs
- allow institutional/safe contact
- block/report (`message_blocks`, `content_reports`)
- minimize phone/email in projections
- moderation hooks

Guardian **no auto-read**.

### 16.2 M19

Feed, posts, media, stories/reels, moderation, future `sponsored` marker.

Teen privacy defaults (private profile, reduced discovery, no dark patterns).

M29 placements attach to M19 posts. **No** second social network.

UNDER_18: no sponsored distribution, no Brand Studio targeted ads, no sensitive targeting, no use of VitaCora/health/precise location/sensitive behavior/inference for ads. Organic community content allowed.

---

## 17. Media / Storage

**M05_CANONICAL_AUTHORITY_RECOMMENDED = YES**

`media_assets`: stable id, bucket, object path, mime, size, owner_kind (USER/ORGANIZATION/PLATFORM), visibility, created_by, created_at, lifecycle, minimal metadata. Versions + links.

Domains store `asset_id` only. Signed URLs are ephemeral RPC output.

Serves: person avatar, pet avatar, org logo, social, VitaCora moments, daycare/service, documents, health attachments.

### Buckets (few, not per module)

| Bucket | Public | Use |
| --- | --- | --- |
| `public-media` | yes | explicitly public renders |
| `private-media` | no | default; signed access |
| `documents` | no | PDFs, health, org docs |
| `moderation-evidence` | no | reports |

**PRIVATE BY DEFAULT** unless the asset visibility is PUBLIC **and** the domain object is public.

Object naming: `{bucket}/{owner_kind}/{owner_id}/{asset_id}/{version}` — no PII in path.

Orphans: `deleted_at` + retention job; do not hard-delete bytes until privacy erasure or retention policy (legal review). Cleanup is a job, not user “Eliminar”.

Legacy bucket `leover`: not created in new baseline.

---

## 18. Location / Temporal

### Location

`location_nodes`: `id`, `kind` (COUNTRY | PROVINCE | LOCALITY), `parent_id`, `name`, `iso_code` nullable, `sort_key`, `active`.

V1 UX Argentina: Provincia → Localidad via stable IDs. Free text is display fallback only, not authority.

Precise location: `geography(Point, 4326)` on lost/found (and similar) with RLS; **never** in public RPCs. Public contracts return approximate zone only.

Teen: precise location PROTECTED; never public; Lost/Found only by purpose/permission. Avoid home/school/routine/realtime/frequent-place leakage.

Pilot default TZ config may be `America/Argentina/Buenos_Aires`. **Not** a domain rule.

### Temporal

| Kind | Type |
| --- | --- |
| Date only (birth, vaccine day) | `date` |
| Event (message, check-in) | `timestamptz` UTC |
| Schedule | `timestamptz` + IANA `zone_id` |

No global GMT-3 hardcode.

---

## 19. Legal / Privacy / Consent

### 19.1 `legal_documents`

`type`, `version`, `locale`, `effective_from`, `content_hash`, `status` (DRAFT / EFFECTIVE / RETIRED), `published_at`.

Types at least: TERMS, PRIVACY, COMMUNITY_RULES. Contextual consents are events against a document or a `consent_code` (VITACORA_SHARE, PRECISE_LOCATION, PUBLIC_PET, DAYCARE_PUBLIC, AI_SPECIFIC, WEB_NONESSENTIAL_COOKIES).

Current texts: **DRAFT PRE-LAUNCH**. No fake CUIT/address/legal email.

### 19.2 `legal_consent_events`

`subject_user_id`, `actor_user_id`, `document_id`, `event_type` (ACCEPT / WITHDRAW / MINOR_ASSENT / GUARDIAN_CONSENT / CONTEXTUAL_GRANT / CONTEXTUAL_WITHDRAW), `occurred_at`, `source`, `evidence` (hash/locale/app version), metadata mínima.

No global `accepted=true`.

Just-in-time: request when the feature appears. No “acepto todo”.

**No marketing consent at signup.** Do not create an active marketing-purpose consent. Schema may later add a marketing document; V1 must not collect it.

Operational communications (security, verification, recovery, bookings, messages, material terms/privacy changes) are documented as service-functional, not marketing.

### 19.3 Account creation (conceptual)

username + basic identity + birth_date/age determination + TERMS accept + PRIVACY acknowledgment + COMMUNITY_RULES when applicable + teen flow when applicable. No unused-feature consents.

### 19.4 `LEGAL_LAUNCH_GATE`

Before public registration: identifiable operator; effective Terms, Privacy, Community Rules; data-rights mechanisms; consent matrix; minor policy legally reviewed; subprocessors as required; final legal review.

This **does not** block canonical design or 03C staging DDL.

---

## 20. Delete / Erasure

### PRODUCT DELETE

Hide / archive / revoke / deactivate / soft-delete per entity (`archived_at`, `revoked_at`, `hidden_at`, `lifecycle_status`). Used for VitaCora hide, stay hide, relationship end, content hide, pet close, grant revoke.

`DELETE_IS_NON_DESTRUCTIVE = YES` for ordinary product/business operations.

### PRIVACY ERASURE — `DEFINED_PRELAUNCH`

Separate `privacy_requests` workflow: ACCESS | RECTIFICATION | UPDATE | ERASURE.

ERASURE may: delete personal data; irreversibly anonymize; restrict retained info; keep only justified records (legal obligation, security, abuse/fraud prevention, rights defense, integrity of legitimate third-party records). **DATA MINIMIZATION.** No “keep just in case”.

No arbitrary retention periods in this design. Final legal review before production.

### Third-party records

Account erasure **must not** arbitrarily destroy vet consultations, daycare stays, org records, or other legitimate authoritative records. Anonymize/minimize the user linkage; keep the operational fact when justified.

`BUSINESS RECORD RETENTION` ≠ `PERSONAL DATA RETENTION`.

---

## 21. Security / RLS

### Principles

```text
auth.uid()
+ platform role (admin paths only)
+ membership / pet relationship
+ permission_codes
+ age_capability_rules
+ grant row / contextual consent
+ teen contact controls
```

ActiveContext = **never** security.
JWT `account_type` = **absent**.
UI = never authority.

Default: revoke direct table writes for clients; RPC `SECURITY DEFINER` with fixed `search_path` for sensitive writes. Helpers `_acl_*` shared — avoid recursive RLS on memberships (helper reads memberships as definer).

### Matrix (domain-level)

| Domain | SELECT | INSERT | UPDATE | ARCHIVE/DELETE |
| --- | --- | --- | --- | --- |
| `persons` self | owner | trigger on signup | owner; birth_date locked after assurance escalate | product: deactivate; erasure: privacy RPC |
| `persons` other | public projection only | no | no | no |
| `persons.birth_date` | self / admin / guardian **only if** explicit safety function | signup | restricted | erasure |
| Guardian | parties + admin | RPC invite | RPC accept/end | end ≠ hard delete |
| Platform assignments | admin | superadmin RPC | superadmin | revoke |
| Organizations | public redacted / members full | RPC create (age capability) | members with org.edit | archive |
| Memberships | members | invite RPC | role RPC | end |
| Pets | responsibility / grant / public redacted | RPC create | pet.edit | archive |
| Responsibility | parties | invite/accept RPC | revoke RPC | event + until |
| Declared health | pet.view/health + grants HEALTH+ | health.manage_declared | same | hide |
| Professional health | clinic membership / grant HEALTH+ / responsible | professional RPC | professional | no user wipe |
| VitaCora moments | owner/authorized vitacora.view; default private | vitacora.manage | same | hide |
| Grants | granter / grantee org members | vitacora.share + age cap | revoke | revoke |
| Proposals | responsible + originator | provider RPC | decide RPC | cancel |
| Custody | responsible + custodian | domain RPC | close RPC | close |
| Bookings | participants | RPC | lifecycle RPC | cancel/archive |
| Daycare public | only if consent | staff | revoke consent | hide media |
| Conversations | participants (org via membership) | RPC | hide message | archive thread |
| Teen inbound DM | contact controls | deny unknown if restricted | — | block/report |
| Social | author / public / friends | author; teen defaults | hide | hide |
| Lost/found public | redacted RPC | create RPC | owner | resolve |
| Precise coords | owner / responders RPC | create | — | never public |
| Reviews | public + parties | only if eligible interaction | hide | hide |
| Donations | public campaign; private contribution | pledge evidence | — | hide |
| Brand placements | public if not under-18 audience | advertiser | — | **no select for UNDER_18 feed** |
| Legal events | subject / actor / admin | RPC accept | withdraw RPC | no delete |
| Privacy requests | subject / admin | RPC | admin process | — |
| Tutorials | self | upsert | upsert | no (omit ≠ revoke) |
| Audit | admin | system | no | retention job only |
| Media private | ACL of parent | upload RPC | — | archive |
| Media public | public | only if parent public | — | hide |

Sponsored feed query **must** exclude `persons.age_band <> ADULT_18_PLUS` (derived). Organic posts remain.

Community essential RPCs have **no** subscription/entitlement check.

---

## 22. Index Strategy

Only indexes justified by PK/FK, uniqueness, or listed access paths.

| Index | Why |
| --- | --- |
| `persons.username` UNIQUE | signup/login |
| `persons(age_band)` or expression on `birth_date` | teen/ads/capability filters |
| `pets.public_code` UNIQUE | public pages |
| `lost_found_alerts.public_code` UNIQUE | `/perdidos` `/encontrados` |
| `adoption_publications.public_code` UNIQUE | `/adopciones` |
| `pet_responsibility_links(pet_id, status)` | active holders |
| `pet_responsibility_links(holder_person_id, status)` | my pets |
| `pet_responsibility_links(holder_organization_id, status)` | org pets |
| `pet_permission_grants(pet_id, subject_id)` | ACL |
| `pet_custody_records(pet_id)` WHERE ACTIVE | now |
| `vitacora_access_grants(pet_id, status)` | active grants |
| `vitacora_access_grants(grantee_person_id)` / org | inbound |
| `vitacora_access_grants(expires_at)` WHERE revoked_at IS NULL | expiry jobs |
| `organization_memberships(organization_id, person_id)` UNIQUE active | ACL |
| `organization_memberships(person_id, status)` | my orgs / ActiveContext |
| `conversation_participants(conversation_id, participant_key)` UNIQUE | inbox |
| `conversation_participants(person_id)` / org | lists |
| `messages(conversation_id, created_at)` | thread |
| `bookings(starts_at, zone_id)` / `(provider_id, starts_at)` | schedule |
| `bookings(pet_id, status)` | pet agenda |
| `lost_found_alerts` GIST on precise geography | matching; not public |
| `legal_documents(type, locale, status, effective_from)` | current version |
| `legal_consent_events(subject_user_id, document_id)` | audit |
| `privacy_requests(subject_user_id, status)` | admin queue |
| `guardian_relationships(minor_user_id, status)` | teen gate |
| `media_asset_links(asset_id)` / `(owner_table, owner_id)` | resolve |
| `tutorial_progress(user_id, tutorial_key)` UNIQUE | reopen |
| `reviews(source_booking_id)` UNIQUE | one review per eligible interaction |
| `location_nodes(parent_id, kind)` | Provincia→Localidad |
| `security_audit_events(occurred_at)` + `(actor_user_id)` | admin |

No speculative covering indexes.

---

## 23. Seed Strategy

**No QA users, no QA pets, no demo social.**

| Seed | Class | Who changes later |
| --- | --- | --- |
| Extensions flags / permission_codes / platform roles | system constants | migration |
| `age_capability_rules` initial rows | system + legal-editable | admin after legal review |
| `legal_documents` DRAFT placeholders (hash of draft files) | system | legal publish |
| Argentina `location_nodes` (provinces + localities) | geography | admin catalog |
| `species` / core `breeds` | catalog | admin |
| `service_categories` / `care_event_types` | catalog | admin |
| `moderation_reason_codes` | catalog | admin |
| Tutorial keys + versions (incl. `TEEN_ACCOUNT_TUTORIAL`, `VITACORA`) | config | admin |
| Bootstrap SUPERADMIN | operational | one-time 03C |

Baseline seed ≠ admin-managed catalogs after launch. Geography and breeds are admin-managed once seeded.

---

## 24. Legacy Mapping

Archive recommendation (analyze-then-name; **do not move files in 03B**):

Current path is `supabase/migrations/` (`001`–`082`). Git history must stay.

For 03C **new Supabase project**: empty `supabase/migrations/` starting at `1000_*` (or `0001` in the new project).

In **this repo**, when 03C starts: `git mv supabase/migrations supabase/migrations-legacy` in a dedicated commit (not history rewrite), then add new `supabase/migrations/` for the canonical project **or** keep canonical SQL under `supabase/canonical/` if both trees must coexist in one repo. Prefer **new project + `migrations-legacy/` archive** so CLI cannot accidentally replay 001–082.

| LEGACY_OBJECT | MODULE | PURPOSE | CANONICAL_REPLACEMENT | ACTION | DEPS | RISK | NOTES |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `users.account_type` | M01 | identity type | capabilities + age/protection | REPLACE | JWT/DTO/UI | High | do not recreate |
| `AppMode` / AccountType nav | Android | nav | ActiveContext | REPLACE | NavGraph | Med | client 03C+ |
| `pet_passports*` / `passport.*` | M14 | snapshot | `vitacora_*` | REPLACE | web 081, M28 | High | no `passport_*` |
| `m14_*` / `m28_*_passport_*` | M14/M28 | RPC | `vitacora_*` / domain RPC | REPLACE | apps | High | |
| `pets.owner_id` | M08 | single owner | `pet_responsibility_links` | REPLACE | lists | High | |
| `TEMPORARY_CUSTODIAN` on responsibilities | M08 | custody | `pet_custody_records` | REBUILD | M10/M23 | Med | |
| `pets.age_years` / `age_months` | M08 | age SoT | birth precision model | REPLACE | forms | Med | |
| `pets.photo_url` / `users.avatar_path` | media | URL identity | `avatar_asset_id` → M05 | REPLACE | UI WIP | Med | |
| `province`/`city` text | geo | free text | `location_nodes` | REBUILD | forms | Med | |
| `user_consents` booleans/versions | M01 | accept | `legal_documents` + events | REBUILD | legal | Med | no single accepted |
| `account_deletion_requests` | M01 | delete | `privacy_requests` | REBUILD | legal | High | ≠ product delete |
| `pets.vaccinations` jsonb | M08 | health blob | declared + M28 tables | REBUILD | M28 | High | |
| `pet_authorizations.capabilities[]` | M08 | family ACL | `pet_permission_grants` | REBUILD | M14 | Med | |
| `organizations.type` single | M03 | label/ACL | `organization_capabilities` | REPLACE | UX | Med | |
| `m20_conversations` two users | M20 | DM | entity participants | REBUILD | org leave | High | |
| `conversations`/`messages` 007 | legacy | chat | M20 | DROP | — | Med | HISTORICAL |
| `posts` + 078 | legacy | social | M19 | DROP | — | Med | |
| `m19_social_posts` | M19 | social | keep concept | KEEP/REBUILD | M05 | Low | |
| `service_bookings` 011 | legacy | bookings | M23 | DROP | — | Med | |
| `m23_bookings` | M23 | bookings | + snapshot/stay | REBUILD | M14 | High | |
| M12 appointments | M12 | 2nd agenda | M23 | REBUILD | M23 | High | |
| `m25_*` cart/orders | M25 | checkout | none V1 | DROP | M24 | Med | FUTURE archive |
| `shop_products` / `payment_intents` | legacy | pay | none V1 | DROP | — | High | |
| `donation_campaigns` 006 | legacy | dual | M17 | DROP | — | Low | |
| `community_events` 006 | legacy | dual | M18 | DROP | — | Low | |
| `shelters` 006 | legacy | dual | org + M16 | REPLACE | — | Med | |
| storage `leover` | M05 | legacy bucket | private/public-media | DROP | — | Low | |
| `get_public_pet` via passport | web | public | `pets.public_code` RPC | REBUILD | 081/082 | High | keep URL paths |
| Tutorial SharedPreferences | M02 | UX | `tutorial_progress` | REBUILD | — | Low | |
| Human birth/guardian (absent) | M01 | teen | persons + guardian | REBUILD | legal | High | |
| `AccountType.TEEN` | — | — | do not create | DROP | — | High | |
| M09 archive-on-adopt | M09 | identity | same pet id | REBUILD | M08 | Med | |
| Timezone AR defaults | several | TZ | config default only | REPLACE | M12/M16 | Med | |
| Dual clinic/provider | M12/M22 | overlap | capabilities + M23 | REBUILD | — | Med | |

---

## 25. Environment Strategy

| Environment | Role | Create now? |
| --- | --- | --- |
| OLD current Supabase | reference / rollback | no — preserve |
| NEW Supabase Staging | canonical dev/test | **not in 03B** — 03C |
| FUTURE Supabase Prod | production | later; **never = staging** |

Config/secrets (future, not created now):

- separate project URL + anon/service keys
- no shared service_role between old/new/prod
- Android/web/KMP flavors: `old` vs `canonical-staging`
- storage buckets created only on new project
- Auth providers configured independently
- no copy of old JWT secret

Remote live verification in this session: **NOT_CONFIRMED** (no unequivocally read-only linked CLI; 03A also did not query `schema_migrations`). Last documented staging snapshot remains RC1: max **077** with gap **039–052**. Repo local: **082**. Drift: YES.

03C first hour: one read-only inventory of old staging (migration versions, counts) **without write**.

---

## 26. SQL Build Order

No SQL in 03B. Future 03C order (minimizes cycles; media `created_by` → `auth.users`, avatars nullable until assets exist):

| Step | File prefix | Contents |
| --- | --- | --- |
| 00 | `1000_extensions` | pgcrypto, postgis, (pgvector if matching) |
| 01 | `1001_catalogs_geography` | location_nodes, species, breeds, permission_codes, service_categories, care_event_types, moderation_reason_codes, age_capability_rules |
| 02 | `1002_person` | persons, privacy, contact controls, friendships, devices, notification prefs |
| 03 | `1003_platform` | roles, permissions, assignments |
| 04 | `1004_legal_guardian_tutorials` | legal_documents, consent_events, privacy_requests, guardian_*, tutorial_progress |
| 05 | `1005_organizations` | orgs, capabilities, branches, memberships, invitations, public profiles |
| 06 | `1006_media` | media_assets, versions, links, buckets |
| 07 | `1007_pets` | pets, lifecycle, responsibility, invitations, permission grants |
| 08 | `1008_custody` | pet_custody_records |
| 09 | `1009_health_declared` | declared health tables |
| 10 | `1010_vitacora` | profiles, moments, grants, proposals, integration links |
| 11 | `1011_social` | posts, comments, reactions, stories |
| 12 | `1012_messaging` | conversations, participants, messages, blocks |
| 13 | `1013_rescue` | lost/found, sightings, matches, adoption, foster |
| 14 | `1014_providers` | providers, offerings, coverage |
| 15 | `1015_bookings_daycare` | slots, bookings, snapshots, stays, care events, incidents, public consents |
| 16 | `1016_veterinary` | professional profiles, affiliations, cares, vaccines, documents |
| 17 | `1017_reputation_events_donations` | reviews, events, donation campaigns/contributions |
| 18 | `1018_moderation_admin_notify` | reports, cases, tickets, notifications, outbox, audit |
| 19 | `1019_brand_studio_support` | campaigns, placements (no under-18 targeting) |
| 20 | `1020_rls_rpc` | `_acl_*`, grants, public RPCs, age/guardian gates |
| 21 | `1021_seeds` | catalogs, AR geography, draft legal rows, tutorial keys |
| 22 | `1022_smoke` | assertions: no account_type, no passport_*, multi-owner, guardian≠owner, under-13 deny, grant NONE still books, moment private, daycare consent NO, under-18 no sponsored select |

Omit: M24, M25 transactional, `passport_*`, `account_type`, `payment_intents`.

---

## 27. Cutover

Design only:

1. Approve REBASE-03B.
2. Final legal **draft/review checkpoint** (does not block 03C staging DDL; blocks public registration).
3. REBASE-03C implementation (SQL in **new** project).
4. Create NEW staging Supabase.
5. Apply canonical baseline 1000–1022.
6. Seeds (no QA data).
7. Storage buckets + policies.
8. Bootstrap SUPERADMIN.
9. Backend smoke (RPC/RLS assertions).
10. Android migrate config/contracts (separate task).
11. Web migrate config/contracts (keep public URL paths).
12. KMP validation.
13. Physical Android QA **only when explicitly requested**.
14. Public web smoke (`/mascota`, `/perdidos`, `/encontrados`, `/adopciones`).
15. Preserve old backend temporarily.
16. Future PROD as a **separate** project.
17. Retire old backend only after signoff.

No dual-write. One active app backend at a time; old retained.

Data: preprod. **No migrate-all.** Do not copy Passport snapshots. If 03C read-only counts show real pilot users, re-evaluate auth import **without** legacy snapshots.

---

## 28. Risk Register

| ID | Risk | Mitigation |
| --- | --- | --- |
| B1 | Replay 001–082 on new project | new project; archive `migrations-legacy`; CLI empty |
| B2 | Treat 03A PENDING as current | this doc + Master/D01 authority |
| B3 | Remote counts unknown | 03C read-only snapshot first |
| B4 | Copy Passport / jsonb vaccines | no data migrate of snapshots |
| B5 | Guardian auto-OWNER / auto-DM | separate tables; smoke tests |
| B6 | Age stored stale / JWT ACL | derive band; server revalidate |
| B7 | UNDER_13 account created | signup RPC deny |
| B8 | FULL_SHAREABLE without age cap | RPC conjunction |
| B9 | Sponsored ads to UNDER_18 | feed RPC exclude; no targeting columns from VitaCora/health/geo |
| B10 | Marketing consent collected | no column/checkbox in signup |
| B11 | Product delete used as erasure | two workflows; privacy RPC only |
| B12 | Erasure destroys vet/stay | anonymize link; keep SoT |
| B13 | Recursive RLS | `_acl_*` definer helpers |
| B14 | Second agenda M12/M23 | M23 only for slots |
| B15 | Org shown N times | ActiveContext by `organization_id` |
| B16 | Public pages break | keep paths; new `public_code` on pets |
| B17 | WIP overwritten | 03B docs only |
| B18 | Legal texts treated as effective | DRAFT PRE-LAUNCH; LEGAL_LAUNCH_GATE |
| B19 | Hardcoded AR TZ as law | config default only |
| B20 | M25 checkout resurrected | omitted from 1000–1022 |
| B21 | Mega JSON health | normalized tables |
| B22 | Tutorial treated as consent | separate table; skip has no ACL effect |
| B23 | Document verification creep | V1 ACCOUNT_CONFIRMED only |
| B24 | Circular person↔media FK | created_by → auth.users; avatar nullable |

---

## 29. Pre-Production Legal Gate

`FINAL_LEGAL_REVIEW_REQUIRED_BEFORE_PROD = YES`

| Item | Design status | Prod status |
| --- | --- | --- |
| PRIVACY_ERASURE_POLICY | DEFINED_PRELAUNCH | legal review |
| LEGAL_MINOR_CONSENT_POLICY | DEFINED_PRELAUNCH | legal review |
| GUARDIAN_VERIFICATION_POLICY | DEFINED_FOR_V1_PRELAUNCH | legal review |
| MINOR_ADVERTISING_POLICY | NO_SPONSORED_ADS_UNDER_18_V1 | legal review |
| Exact 13–15 / 16–17 action matrix | schema + catalog | legal review |
| Terms / Privacy / Community texts | DRAFT PRE-LAUNCH | LEGAL_LAUNCH_GATE |
| Operator COMUNIDAPP S.A.S. | name planned, not constituted | constitution + CUIT + domicile + legal emails |
| Subprocessors list | placeholder | before public registration |
| Retention periods (numeric) | not invented | legal + PEN-016 |
| Marketing | not collected | remains unused unless new purpose |

This gate **does not** block REBASE-03C staging implementation.

---

## 30. Working tree / remote (this session)

| Field | Value |
| --- | --- |
| BRANCH | `main` |
| HEAD | `32577b9917500fe5e4096129f422ba29da4228b2` |
| Working tree | dirty (unrelated WIP) |
| UNRELATED_WIP_PRESERVED | YES |
| REMOTE_LIVE_VERIFICATION | NOT_CONFIRMED |
| REMOTE_MAX_MIGRATION_CONFIRMED | NO (documented RC1: 077 + gap 039–052) |
| CURRENT_AUTH_USERS_CONFIRMED | NO |
| Files changed by this task | `docs/02-arquitectura/REBASE-03B-canonical-baseline-design.md` only |

---

## 31. Tutorial / VitaCora onboarding / Community (schema support)

`tutorial_progress`: `(user_id, tutorial_key, version)` unique; `viewed_at`, `skipped_at`, `completed_at`, `reopened_at`. All skippable/reopenable. Skip ≠ consent, permissions, terms, or privacy change.

Keys include: `CONOCIENDO_LEOVER`, `VITACORA`, `VITACORA_SHARE`, `FAMILIA`, `LOST_FOUND`, `ADOPTION`, `SHELTER_ONG`, `RESCUER`, `FOSTER`, `VETERINARY`, `DAYCARE`, `PROVIDERS`, `TEEN_ACCOUNT`.

VitaCora tutorial copy (content/config, not special SQL): “Su vida. Su historia. Sus cuidados.” Vita=vida, Cora=corazón, evoca bitácora. Teach once; reopen from Ayuda.

Compromiso Comunidad: no billing check on essential people/rescuer/shelter/NGO RPCs listed in Master §3.21. Professional/commercial (M28, M29, provider subscriptions) remain separable.

Rescuer = PERSON + contextual capability. Not AccountType. Not a required subscription.

Donations: alias/CBU direct; LeoVer 0%; no fund custody; no third-party checkout.

---

## 32. Performance guardrails

Avoid: mega JSON as SoT, unbounded arrays as ACL, N+1 grant loops (batch `_acl_pet_permissions`), missing FK indexes listed in §22, duplicated authorities, recursive RLS, unbounded public `SELECT *`. Public RPCs are redacted, paginated, and index-backed.

No enterprise event-sourcing bus. Domain history tables where listed; `security_audit_events` only for security-sensitive actions (grants, guardian, legal, privacy, admin, responsibility changes).

---

Fin del diseño REBASE-03B. No SQL. No implementación.
