# REBASE-03E — Canonical schema gap closure

**Producto:** LeoVer  
**Fecha:** 15 de agosto de 2026  
**Rama:** `main`  
**HEAD:** `679fd3d63197b7318e17c0b48351e0719563c212`  
**Tipo:** SCHEMA-ONLY + STAGING VERIFICATION. Forward migration 1023. Sin commit. Sin producción.

**Gobierno:** Master v1.2 > D01 v1.3 > REBASE-03B > REBASE-03C. REBASE-03D es evidencia de consumidor, no autoridad de producto.

**Veredicto:** `REBASE_03E_SCHEMA_GAPS_CLOSED`

---

## 1. Isolation

| Key | Value |
| --- | --- |
| `CANONICAL_STAGING_PROJECT` | `tobqbddfcyitwgbkthhy` |
| `LEGACY_PROJECT` | `wystsapjfpdtoprlmizz` |
| `LEGACY_PROJECT_CHANGED` | NO |
| `BASELINE_1000_1022_MODIFIED` | NO |
| `NEW_FORWARD_MIGRATION` | `20260815212500_1023_consumer_enablement.sql` |
| `PRODUCTION_CHANGED` | NO |
| `COMMIT_PERFORMED` | NO |
| `UNRELATED_WIP_PRESERVED` | YES |

Assert antes de dry-run/apply: `TARGET_PROJECT_REF != wystsapjfpdtoprlmizz`.

---

## 2. Blockers heredados de REBASE-03D

| Gap | 03D evidence |
| --- | --- |
| A. Pet update | `pets` SELECT-only; no `canon_update_pet` |
| B. Pet avatar persist | `canon_register_media` existe; no write de `pets.avatar_asset_id` |
| C. M05 media/storage | sin SELECT `media_assets`; sin policies `storage.objects` |
| D. Declared health write | tablas 1009 existen; sin RPC/INSERT |
| E. Public adoption | no `canon_public_adoption` |
| F. Username availability | uniqueness al insert; sin boolean RPC |
| G. Pet holders listing | `pet_responsibility_links` sin SELECT; no list RPC |

03D se conserva como evidencia histórica. Su veredicto `REBASE_03D_BLOCKED` no se reescribe.

---

## 3. Schema solution (1023)

Forward-only. No rediseño de tablas. No wrappers legacy. No índices nuevos (el unique `(bucket, object_path)` y `persons_username_uidx` cubren los paths nuevos).

### 3.1 Authority helpers

- `_acl_can_edit_pet` = admin **o** `pet.edit` (holder + grant/OWNER) **o** org responsable activa + `org.pets.manage`
- `_acl_can_manage_declared_health` = admin **o** `health.manage_declared` **o** org responsable + `org.pets.manage`
- `created_by_user_id` no es autoridad
- Guardian / custodia temporal no implican write

### 3.2 RPC signatures

| RPC | Auth | Returns |
| --- | --- | --- |
| `canon_update_pet(p_pet_id, p_name, p_species, p_breed_id, p_sex, p_size, p_home_locality_id, p_birth_*)` | authenticated + edit | `uuid` |
| `canon_set_pet_avatar(p_pet_id, p_media_asset_id)` | authenticated + edit | `uuid` |
| `canon_record_pet_allergy` / `_medication` / `_vaccination` / `_parasite_treatment` / `_weight` / `_condition` | authenticated + `health.manage_declared` | `uuid` |
| `canon_set_pet_care_instructions` | authenticated + `health.manage_declared` | `uuid` |
| `canon_public_adoption(p_code)` | anon + authenticated | sanitized `jsonb` or null |
| `canon_is_username_available(p_username)` | anon + authenticated | `boolean` |
| `canon_list_pet_holders(p_pet_id)` | authenticated + holder/admin | `jsonb` array |

`canon_update_pet` no toca responsibility, custody, VitaCora, health, creator, moderation, avatar. Birth: `EXACT_DATE` / `MONTH_PRECISION` / `YEAR_PRECISION` / `ESTIMATED` / `UNKNOWN`. No `age_years`. Optional `sex`/`size`/`breed_id`/`home_locality_id` null = leave unchanged.

### 3.3 Avatar

`canon_set_pet_avatar` acepta `NULL` para quitar foto (reemplazo UX cubre el caso; clear queda disponible). Verifica asset existente, `owner_kind=PERSON`, `owner_person_id=actor`, mime `image/%`, lifecycle READY/UPLOADING. No signed URL. El asset previo no referenciado se marca `ARCHIVED` + `orphan_candidate`. No GC.

Flujo foto:

1. `canon_register_media`
2. upload al `bucket`/`object_path` registrado (policy INSERT)
3. `canon_set_pet_avatar`
4. reload: `pets.avatar_asset_id` → `media_assets` → URL runtime

### 3.4 Media / storage

- `MEDIA_OWNER_KIND = PERSON` vía `owner_person_id`
- SELECT `media_assets`: owner PERSON, holder del pet si es avatar, miembro org, `visibility=PUBLIC`, admin
- No `authenticated → all`. No `anon → all`
- Storage SELECT/INSERT en los 4 buckets existentes. **No DELETE/UPDATE global**
- INSERT solo si existe metadata `media_assets` del actor (`PERSON`) en el mismo bucket+path
- `moderation-evidence` no admite INSERT authenticated

### 3.5 Health

Siete RPCs explícitos. `source` forzado `DECLARED`. Actor = `auth.uid()`. No PROFESSIONAL/VERIFIED desde estos comandos.

### 3.6 Public adoption

Retorna solo si `status='OPEN'` y pet `ACTIVE`. Caso inválido/privado/inexistente → `null` (404 segura). Sin `pet_id`, person/org IDs, email, phone, coords, notes, health, audit.

### 3.7 Username

Normalización = índice único: `lower(btrim(username))`. No reserva. Race → unique constraint.

### 3.8 Holders

`PERSON OWNER` / `PERSON AUTHORIZED` / `ORGANIZATION RESPONSIBLE`. No público. No guardian/custodian implícito.

---

## 4. Apply

| Step | Result |
| --- | --- |
| Dry-run | `20260815212500_1023_consumer_enablement.sql` only |
| Dry-run new count | 1 |
| Legacy/passport/marketplace in dry-run | 0 |
| Apply | PASS (`supabase db push --yes --workdir infra/supabase-canonical`) |
| History | 1000–1023, sin gaps |

---

## 5. Smoke (Staging)

Script: `infra/supabase-canonical/tests/03e_consumer_enablement_smoke.sql` (no es migration productiva). QA `qa_03e_*` / `*@leover.invalid`. Cleanup al final. Leftover = 0.

No se insertó `storage.objects` por SQL: `storage.protect_delete()` impide borrar objects por SQL. La policy de upload existe; el upload físico es QA 03F.

| Check | Result |
| --- | --- |
| Pet update authorized / stranger | PASS / DENIED |
| Creator without current authority (no org path) | DENIED |
| Photo create / replace / preserve on text edit / reload | PASS |
| Private media stranger/anon | DENIED |
| Explicit PUBLIC asset | readable |
| Health writes DECLARED; stranger/authorized-teen/custodian | PASS / DENIED |
| Public / private / unknown adoption | record / null / null |
| Username available / taken / case | PASS |
| Holders multi-owner + org; stranger | PASS / DENIED |

---

## 6. Consumer wiring (mínimo)

Preferencia schema-only. Ajuste de firma 03D:

- `p_asset_id` del DTO → `p_media_asset_id`
- `p_breed` texto no existe en schema → `p_breed_id` null
- health UI → RPCs explícitos (no JSON genérico)

Web `/adopciones/` llama `canon_public_adoption(p_code)`.

---

## 7. Residual / 03F

- QA físico en emulador/browser (foto upload real, página pública live)
- Dominios CONTRACT_ONLY de 03D (provider/booking/messaging) siguen fuera de scope
- iOS/Xcode no ejecutado (Windows)

`READY_FOR_REBASE_03F_PHYSICAL_QA = YES`
