# REBASE-03C — Canonical Staging Implementation

**Producto:** LeoVer
**Sociedad operadora prevista:** COMUNIDAPP S.A.S. (aún no constituida)
**Fecha:** 15 de agosto de 2026
**Rama:** `main`
**HEAD:** `32577b9917500fe5e4096129f422ba29da4228b2`
**Tipo:** IMPLEMENTATION — SQL + nuevo Supabase Staging. Sin cutover de app/web/KMP. Sin commit/push.

**Gobierno de entrada:**

- `FINAL_GOVERNANCE_READY_FOR_REBASE_03B`
- `REBASE_03B_DESIGN_READY`
- `READY_FOR_REBASE_03C_IMPLEMENTATION = YES`

**Veredicto de esta etapa:** `REBASE_03C_CANONICAL_BASELINE_CLEAN`
**Siguiente:** commit aislado del bloque canónico, luego REBASE-03D (cutover de clientes). No cutover todavía.

---

## 1. Isolation

| Key | Value |
| --- | --- |
| `LEGACY_SUPABASE_WORKDIR` | `supabase/` (repo root; migrations `001`–`082` intactas) |
| `CANONICAL_SUPABASE_WORKDIR` | `infra/supabase-canonical/` |
| `LEGACY_BACKEND_PROJECT_REF` | `wystsapjfpdtoprlmizz` |
| `STAGING_PROJECT_NAME` | LeoVer Staging |
| `STAGING_PROJECT_REF` | `tobqbddfcyitwgbkthhy` |
| `TARGET_DIFFERS_FROM_LEGACY` | YES |
| `LEGACY_BACKEND_CHANGED` | NO |
| `LEGACY_WORKDIR_STILL_LINKED_TO` | `wystsapjfpdtoprlmizz` (no relink) |
| `CANONICAL_WORKDIR_LINKED_TO` | `tobqbddfcyitwgbkthhy` |

Assert ejecutado antes de cada escritura remota:

`TARGET_PROJECT_REF != wystsapjfpdtoprlmizz`

CLI usada: Supabase CLI `2.109.1` con `--workdir infra/supabase-canonical`.
No se ejecutó `supabase start`, Docker local, emulador, Gradle, web/KMP build, ni APK.

---

## 2. Migration map

Logical IDs 1000–1022 son el baseline de REBASE-03B.
1023–1025 son *forward fixes* de implementación (no rediseño de producto).

Archivos físicos (formato CLI `<timestamp>_<name>.sql`):

| Logical | Physical file |
| --- | --- |
| 1000 | `20260815170000_1000_extensions.sql` |
| 1001 | `20260815170100_1001_catalogs_geography.sql` |
| 1002 | `20260815170200_1002_person.sql` |
| 1003 | `20260815170300_1003_platform.sql` |
| 1004 | `20260815170400_1004_legal_guardian_tutorials.sql` |
| 1005 | `20260815170500_1005_organizations.sql` |
| 1006 | `20260815170600_1006_media.sql` |
| 1007 | `20260815170700_1007_pets.sql` |
| 1008 | `20260815170800_1008_custody.sql` |
| 1009 | `20260815170900_1009_health_declared.sql` |
| 1010 | `20260815171000_1010_vitacora.sql` |
| 1011 | `20260815171100_1011_social.sql` |
| 1012 | `20260815171200_1012_messaging.sql` |
| 1013 | `20260815171300_1013_rescue.sql` |
| 1014 | `20260815171400_1014_providers.sql` |
| 1015 | `20260815171500_1015_bookings_daycare.sql` |
| 1016 | `20260815171600_1016_veterinary.sql` |
| 1017 | `20260815171700_1017_reputation_events_donations.sql` |
| 1018 | `20260815171800_1018_moderation_admin_notify.sql` |
| 1019 | `20260815171900_1019_brand_studio_support.sql` |
| 1020 | `20260815172000_1020_rls_rpc.sql` |
| 1021 | `20260815172100_1021_seeds.sql` |
| 1022 | `20260815172200_1022_smoke.sql` |

`CANONICAL_PHYSICAL_MIGRATION_COUNT = 23`
`CANONICAL_MIGRATION_RANGE = 20260815170000 … 20260815172200`
`CORRECTIVE_MIGRATIONS_REMAINING = 0` (1023–1025 absorbidas; ver §11)

Cada archivo declara `-- LeoVer Canonical Baseline` y `-- Logical migration: NNNN`.

---

## 3. Dry-run and apply

Primer `supabase db push --dry-run` (workdir canónico, target `tobqbddfcyitwgbkthhy`):

- listó **exclusivamente** 1000–1022
- `LEGACY_MIGRATIONS_IN_DRY_RUN = NO`
- `PASSPORT_MIGRATIONS_IN_DRY_RUN = NO`
- `MARKETPLACE_MIGRATIONS_IN_DRY_RUN = NO`

Apply:

| Batch | Result |
| --- | --- |
| 1000–1022 | SUCCESS |
| 1023 | SUCCESS (después de smoke AGE_CAPABILITY_DENIED) |
| 1024 | SUCCESS (después de smoke `media_assets_owner_xor`) |
| 1025 | SUCCESS (corrige actor del test unauthorized) |

`supabase_migrations.schema_migrations` en Staging (03C.1, post-reset): **23** filas (1000–1022).

Advertencia CLI al pushear: fallo de cache Docker (`Docker Desktop` ausente). El apply remoto **sí** completó. No se inició stack local.

---

## 4. Schema summary (remote Staging)

| Métrica | Valor |
| --- | --- |
| `CANONICAL_ENTITY_COUNT_ACTUAL` | 109 (`public` base tables) |
| `RLS_ENABLED_TABLES` | 109 |
| `RPC_FUNCTION_COUNT` | 32 `canon_*` (47 funciones `public` en total, incl. `_acl_*` / helpers) |
| `INDEX_COUNT` | 165 (`pg_indexes` schema `public`) |
| `BUCKET_COUNT` | 4 |

Buckets: `public-media` (public), `private-media`, `documents`, `moderation-evidence` (private).

Ausencias canónicas verificadas en remoto:

- `account_type` columns = 0
- `appmode` columns = 0
- tablas `*passport*` = 0
- `carts` / `orders` / `payment_intents` / `shop_products` = 0
- `pets.owner_id` ausente (assert 1022)
- `vitacora_*` presente (5 tablas)

Seeds remotos:

- `location_nodes` = 39 (1 COUNTRY + 24 PROVINCE + localities de muestra)
- kinds = `COUNTRY` / `PROVINCE` / `LOCALITY` (como 03B)
- `legal_documents.status` = solo `DRAFT` (PRE-LAUNCH; sin textos vigentes)

---

## 5. Smoke results

Función: `public.canon_smoke_suite()` sobre Staging nuevo.

QA efímero: `*@leover.invalid` / `qa_*`. No datos personales reales. Cleanup al final de la suite.

| Área | Resultado |
| --- | --- |
| AUTH/PERSON + username unique index | PASS (`persons_username_uidx`) |
| UNDER_13 autonomous account | PASS (`under13_denied=true`) |
| Guardian adult path | PASS |
| Organization + membership + capabilities | PASS |
| Multi-owner + authorized + org responsible + history + provenance | PASS |
| Custody open/close; responsibility unchanged | PASS |
| Declared health | PASS |
| VitaCora moment PRIVATE default + hide | PASS |
| Grants ESSENTIAL / HEALTH / ESSENTIAL_AND_HEALTH / FULL_SHAREABLE + expiry + revoke | PASS |
| Proposal ACCEPTED (writes allergy) + REJECTED + hide integration | PASS |
| Provider + booking + instruction snapshot | PASS |
| Daycare check-in / event / incident / checkout; public consent DEFAULT NO; stay→VitaCora | PASS |
| Messaging person–person + person–organization + `actor_user_id` | PASS |
| Veterinary professional + clinic affiliation + provenance | PASS |
| Media asset; no `signed_url` in metadata | PASS |
| Location locality + protected precise geography | PASS |
| Legal document + guardian consent event + privacy ACCESS | PASS |
| Product hide (`vitacora_moments.hidden_at`) | PASS |
| `public_code` on pet | PASS |
| Unauthorized stranger cannot create moment | PASS |
| Authorized owner path succeeds | PASS |

`QA_DATA_CLEANED = YES` (0 `qa_%` persons / 0 `qa-%@leover.invalid` auth users after suite).

---

## 6. Deviations from REBASE-03B

No se cambió producto. Tras 03C.1 no quedan forward fixes.

1. **M05 owner_kind** — 03B escribió `USER/ORGANIZATION/PLATFORM`. `USER` era alias de sujeto de dominio (persona), no rol de plataforma ni `auth.users` como identidad. Baseline canónico: `PERSON | ORGANIZATION | PLATFORM` + `owner_person_id`. Sin alias `USER`.
2. **`_acl_age_allows`** — `requires_guardian_confirmation` no aplica a `ADULT_18_PLUS` (adulto otorga `FULL_SHAREABLE` sin fila de tutor). Teens siguen el gobierno.
3. **Institutional thread** — ownership operativo vía `conversation_participants.ORGANIZATION` + `messages.actor_user_id`. No hay columna `conversations.organization_id`.
4. **Unauthorized gate** — el deny del smoke pasa por RPC `security definer` + `_acl_pet_permission`, no solo por policy RLS de tabla.
5. **Geography seed** — Argentina: 24 provincias + localities de muestra (no catálogo municipal completo). Compatible con kinds 03B.
6. **Legal** — filas DRAFT placeholder; no textos vigentes. Alineado a 03B / Master.
7. **1022 apply-time asserts** — no ejecutan el flujo interactivo; ese flujo vive en `canon_smoke_suite()`.

No se introdujo `AccountType`, `AppMode`, `passport_*`, marketplace V1, ni wrappers de compatibilidad.

---

## 7. Known issues

- CLI no puede cachear catálogo pg-delta sin Docker Desktop. No bloquea Staging remoto.
- `canon_smoke_suite` es `security definer` (herramienta de QA). No es contrato de app.
- Contraseña DB de Staging existe en `infra/supabase-canonical/.env` (gitignored). No commitear.
- Workdir legacy sigue linkeado al backend viejo. No relinkear.
- App/web/KMP siguen apuntando al backend anterior. Eso es correcto hasta 03D.
- `FINAL_LEGAL_REVIEW_REQUIRED_BEFORE_PROD = YES` (no bloquea Staging).
- Políticas de erasure/consent/guardian/ads: `DEFINED_*` prelaunch; no hay cascade destructivo automático.

---

## 8. Rollback / reference status

| Superficie | Estado |
| --- | --- |
| Backend viejo `wystsapjfpdtoprlmizz` | intacto; rollback de producto actual |
| Migrations `001`–`082` en `supabase/` | no movidas, no renombradas, no aplicadas al Staging nuevo |
| Staging canónico `tobqbddfcyitwgbkthhy` | reset + baseline limpio 1000–1022 |
| Clientes (Android / web / KMP) | sin cutover |
| Producción / `leover.com.ar` | no tocados |

Si hace falta recrear Staging en preprod: reportar antes de cualquier acción destructiva. En esta etapa no se recreó el proyecto.

---

## 9. Readiness for app cutover (REBASE-03D)

`READY_FOR_REBASE_03D_APP_CUTOVER = YES`

El Staging canónico existe, el dry-run no arrastró legacy, el baseline aplica, y el smoke backend pasó.

El baseline limpio ya está en Staging. Falta el **commit aislado** del bloque canónico. 03D es el único lugar para cambiar `local.properties` / BuildConfig / env web / KMP / secrets.

---

## 10. What this stage did not do

- No commit / no push
- No cutover de Android, web, KMP, iOS
- No cambio de `leover.com.ar`, Cloudflare, GitHub Actions secrets
- No escritura en `wystsapjfpdtoprlmizz`
- No migración de datos QA/prod del backend viejo
- No textos legales vigentes
- No erasure real de personas
- No Master / D01 / 03A / 03B editados

---

## 11. Canonical Baseline Consolidation (REBASE-03C.1)

Objetivo: empty Staging → baseline 1000–1022 → PASS, sin migrations correctivas.

### Fixes absorbidos

| Fix | Destino canónico | Qué se absorbió |
| --- | --- | --- |
| 1023 | `1020_rls_rpc` → `_acl_age_allows` | Guardian confirmation solo si banda ≠ `ADULT_18_PLUS` |
| 1024 | `1006_media` + `canon_register_media` | `USER` no se conservó. Ver semántica M05 |
| 1025 | `1022_smoke` → `canon_smoke_suite` | Unauthorized = stranger sin vínculo a la mascota; cleanup endurecido |

Archivos 1023/1024/1025 **eliminados**. No quedan correctivas.

### M05 USER semantics

| Campo | Valor |
| --- | --- |
| `M05_USER_SEMANTICS` | `PERSON_LEGACY_ALIAS` |
| `M05_CANONICAL_TERM` | `PERSON` |
| `CANONICALIZE_TO_PERSON` | YES |
| `PERSON_IDENTITY_AMBIGUITY_REMAINS` | NO |

`USER` en 03B (`owner_kind USER/ORGANIZATION/PLATFORM`) nombraba al **sujeto de dominio** (persona dueña del asset), no al rol de plataforma `USER` ni a `auth.users` como identidad distinta. No se creó alias de compatibilidad: no hay consumidor de app todavía.

Schema canónico:

- `owner_kind` ∈ `PERSON` \| `ORGANIZATION` \| `PLATFORM`
- `owner_person_id` → `persons.user_id`
- XOR: `PLATFORM` o `holder_xor_ok(owner_kind, owner_person_id, owner_organization_id)`

### Fresh rebuild

| Paso | Resultado |
| --- | --- |
| Assert target | `tobqbddfcyitwgbkthhy` ≠ `wystsapjfpdtoprlmizz` |
| Comando | `supabase db reset --linked --yes --no-seed --workdir infra/supabase-canonical` |
| Legacy tocado | NO |
| Apply | 1000–1022 only |
| Dry-run post-reset | `Remote database is up to date` |
| Correctivas en dry-run | 0 |
| Legacy/passport/marketplace en dry-run | 0 |

### Migration target

`CANONICAL_LOGICAL_RANGE = 1000–1022`
`CANONICAL_PHYSICAL_MIGRATION_COUNT = 23`
`CORRECTIVE_MIGRATIONS_REMAINING = 0`

### Schema parity vs 03C (pre-consolidación)

| Métrica | 03C (con 1023–1025) | 03C.1 (limpio) |
| --- | --- | --- |
| Entities | 109 | 109 |
| RLS tables | 109 | 109 |
| `canon_*` RPC | 32 | 32 |
| Indexes | 165 | 165 |
| Buckets | 4 | 4 |

Única diferencia de contrato: `media_assets.owner_user_id` + kind `USER` → `owner_person_id` + kind `PERSON`. Misma capacidad.

### Smoke 03C.1 (fresh-from-zero)

Todos PASS: MULTI_OWNER, ORG_RESPONSIBILITY, RESPONSIBILITY_HISTORY, CUSTODY, HEALTH, VITACORA_MOMENT, VITACORA_GRANTS, PROPOSAL, PROVIDER, BOOKING, DAYCARE, VETERINARY, INSTITUTIONAL_MESSAGING, MEDIA, LOCATION, TEMPORAL, AGE_GUARDIAN, LEGAL_DOCUMENT, CONSENT_EVENT, PRIVACY_REQUEST, PUBLIC_FOUNDATION, RLS_UNAUTHORIZED, RLS_AUTHORIZED.

QA efímero limpiado (0 filas `qa_%`).
