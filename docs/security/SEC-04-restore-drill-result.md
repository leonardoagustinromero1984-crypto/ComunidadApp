# SEC-04C — Restore drill result (LeoVer)

Date (UTC): 2026-09-04  
Authorization: Leonardo — project `wystsapjfpdtoprlmizz` used as isolated **LeoVer SEC-04 RESTORE TEST**.  
Canonical STAGING (`tobqbddfcyitwgbkthhy`) was **source only**. It was not reset, not restored onto, and not migrated.

Dumps and object copies live **outside Git**. No passwords, tokens, JWT, service_role values, emails, or private row content in this file.

## Identity check (recorded before restore)

| Role | Project ref | CLI workdir |
|---|---|---|
| SOURCE | `tobqbddfcyitwgbkthhy` (LeoVer Staging, `linked=true` on canonical workdir) | `infra/supabase-canonical` |
| TARGET | `wystsapjfpdtoprlmizz` (Dashboard name still **LeoVer**; designated SEC-04 RESTORE TEST) | repo-root `supabase/` |

Never inverted.

## Historical preservation (before any destructive step)

Private root: `%LOCALAPPDATA%\LeoVer-SEC04C\20260904-121731`  
Created: `2026-09-04T15:17:31Z`  
Git: **not tracked** (outside the repository).

| Artifact | Type | Size | SHA-256 |
|---|---|---|---|
| `historical/schema.sql` | logical dump `public,auth,storage,supabase_migrations` | 2,462,477 | `851205CF6A44ECA29DAAEE5E7C3D079FA558FBB94725CABAA30A9CFD1DD093E3` |
| `historical/data.sql` | data-only COPY | 2,553,736 | `8BCF3F697D27720FAA210E95F13C3E60F6F744436CFE662172B03759A76D5C71` |
| 2 objects `public-media` | Storage byte copy | 1,242,398 | `7CA4A339…` and `02D85A87…` (full hashes in private MANIFEST) |

Historical Edge (inventory only, **no secret values copied**): function `push` v2, `verify_jwt=true`. Secret **names** remain as previously inventoried. They were **not** copied onto the restore target.

Validation: dump files exist; 2 objects preserved; STOP condition not triggered.

## STAGING backup (source)

Method: `supabase db dump --linked` (schema, then `--data-only --use-copy`) from canonical workdir. Schemas: `public`, `auth`, `storage`, `supabase_migrations`.  
STAGING remained `ACTIVE_HEALTHY` and was not modified.

| Artifact | Size | SHA-256 |
|---|---|---|
| `staging/schema.sql` | 735,909 | `A9962C26EE0D6F7259DB384A9BA2B7C9F9D5FA2BFF16A7AB9E26EA24B7DBB059` |
| `staging/data.sql` | 1,693,600 | `459C0FF823BB6DB5F1D91082DF76E94803000ED9AC1A59ED4F41B264B756BF2B` |

| Clock | UTC |
|---|---|
| BACKUP START | 2026-09-04T15:29:35Z |
| BACKUP END | 2026-09-04T15:37:19Z |
| Backup duration | ~447 s (includes CLI Postgres image pull) |

## Restore (TARGET only)

Mechanism: `DROP SCHEMA public CASCADE` + `CREATE SCHEMA public` on TARGET, then `psql` as `postgres` applying the STAGING dumps. Connection user suffix confirmed `wystsapjfpdtoprlmizz`.

| Clock | UTC |
|---|---|
| RESTORE START | 2026-09-04T15:45:30Z |
| Schema apply END | 2026-09-04T15:54:00Z |
| Data apply START | 2026-09-04T15:56:00Z |
| Data apply END | 2026-09-04T15:56:59Z |
| PostGIS + geography tables repair | 2026-09-04T16:03:04Z – 16:03:17Z |
| VERIFICATION END | 2026-09-04T16:06:30Z |

Restore duration (schema+data+PostGIS repair): ~18 min.  
Exercise duration (backup start → verification end): ~37 min.

The historical live `public` schema on TARGET was wiped by design. Recovery of that old dataset is **only** the private historical dump. Historical data was **not** reloaded after the drill.

## Database

**PASS** (after documented PostGIS repair).

| Check | STAGING | TARGET after drill |
|---|---|---|
| public tables / RLS on / RLS off | 130 / 130 / 0 | 130 / 130 / 0 |
| public FKs | 265 | 265 |
| public functions / SECURITY DEFINER | 299 / 273 | 299 / 273 |
| public policies | 33 | 33 |
| migrations 1061–1065 | 5 | 5 |
| max `schema_migrations.version` | `20260902220000` | `20260902220000` |
| `schema_migrations` row count | 66 | 148 (leftover historical versions) |
| PostGIS | `extensions` | `extensions` (created during repair) |

First schema pass failed `extensions.geography` (PostGIS absent after `DROP SCHEMA public CASCADE`). Three tables were missing until `CREATE EXTENSION postgis WITH SCHEMA extensions` and a targeted replay of those DDL/COPY statements. Counts then matched: `lost_found_alerts=3`, `lost_found_sightings=0`, `service_providers=0`.

## Auth

**FAIL** for usable identity recovery.

The dump **contains** `auth.users`, `auth.identities`, and `auth.mfa_factors`. Hosted Auth tables are **not owned** by the restore login. `COPY auth.users` failed (`users_email_partial_key`). TARGET kept **4** historical users vs STAGING **8**.

| Check | STAGING | TARGET |
|---|---|---|
| `auth.users` | 8 | 4 (historical, not replaced) |
| `auth.identities` | 8 | 12 (historical + inserted dump rows) |
| `auth.mfa_factors` / verified | 1 / 1 | 1 / 1 (not bound to restored users) |
| `persons` with matching `auth.users` | 2 / 2 | 0 / 2 |
| `platform_admin_identities` with matching `auth.users` | 6 / 6 | 0 / 6 |

Passwords were not changed to force a pass. No backdoors.  
**A logical `db dump` of `auth` does not restore a login-capable Auth database onto an existing hosted project.**

## Storage metadata

**PARTIAL.**

`COPY storage.buckets` failed (`buckets_pkey`: historical buckets kept).  
`COPY storage.objects` inserted STAGING metadata rows into the **historical** `public-media` bucket.

| | STAGING | TARGET |
|---|---|---|
| buckets | 5 canonical (`public-media`, `private-media`, `documents`, `moderation-evidence`, `vitacora-import`) | 7 historical (`leover`, `public-media`, `profile-avatars`, `organization-media`, `organization-documents`, `moderation-evidence`, `support-attachments`) |
| `storage.objects` rows | 12 | 14 (2 historical + 12 dump metadata) |

Canonical STAGING buckets `private-media`, `documents`, `vitacora-import` were **not** created.

## Storage bytes

**NOT COVERED BY DB BACKUP.**

STAGING object bytes were not copied in this drill. Historical 2 JPEGs remain on TARGET `public-media` from before the drill. Metadata rows without bytes are orphans.

**STORAGE BYTES REQUIRE SEPARATE BACKUP/RESTORE STRATEGY.**

## Edge Functions

**EDGE RECOVERY PROCEDURE: PASS** (procedure, not automatic copy).

DB restore does not deploy functions.

| | STAGING live | TARGET live after DB restore | Repo |
|---|---|---|---|
| `admin-staff` | v5, `verify_jwt=true` | absent | `infra/supabase-canonical/supabase/functions/admin-staff` |
| `vitacora-import-analyze` | v2, `verify_jwt=true` | absent | `infra/supabase-canonical/supabase/functions/vitacora-import-analyze` |
| `push` | not in current attack surface | v2, `verify_jwt=true` (historical leftover) | not deployed as part of this drill |

Required recovery: deploy from repo → configure **new** secrets on the destination → validate caller JWT/AAL2. Do **not** copy historical Firebase/FCM secret values.

## Security verification (TARGET only; not a pentest)

| Control | Result |
|---|---|
| SEC-01 RLS / anon | **PASS** — `rls_off=0`; `anon` cannot INSERT `country_markets`; `anon` cannot SELECT `platform_admin_identities` |
| SEC-02 MFA/AAL2 structures | **PASS** (structures) — `_canon_require_admin_aal2`, `_canon_admin_aal`, `get_admin_session`, `has_permission`, `admin_begin_login` present, SECURITY DEFINER, `search_path` pinned. **Usable admin login: FAIL** (Auth identities not restored) |
| SEC-03 rate limits / flags | **PASS** — 21 policies, 3 flags, `_canon_consume_rate_limit` present |
| SEC-05 1064/1065 | **PASS** — migrations present; `authenticated` cannot INSERT `platform_admin_identities`; platform roles 5 / permissions 48 / role_permissions 99 |

## VitaCora / catalogs / data counts

Compared without private payloads. Core counts matched STAGING after restore:

persons 2, pets 3, pet_responsibility_links 5, vitacora_profiles 3, vitacora_moments 1, social_posts 6, messages 0, conversations 0, organizations 0, species 22, breeds 266, pet_health_products 26, platform_admin_identities 6.

VitaCora public sequence (not consumed): `last_value=57`, `is_called=true`, `max(public_vitacora_number)=55`. Next `nextval` would be 58 — **no collision**.

## RPO / RTO

| Metric | Observed |
|---|---|
| RPO (this mechanism) | Point-in-time of the logical dump (`BACKUP END` 2026-09-04T15:37:19Z). Writes after that instant are not in the dump. **Not PITR.** |
| Provider automatic backups / PITR / retention | **MANUAL DASHBOARD VERIFICATION REQUIRED** — this drill does not replace that check |
| RTO observed (restore + verify) | ~20 min (`RESTORE START` → `VERIFICATION END`) |
| RTO observed (full exercise incl. dump) | ~37 min (`BACKUP START` → `VERIFICATION END`) |

Native Dashboard Restore was not used.

## Problems encountered

1. CLI `db dump` requires Docker Postgres image.  
2. Restore login is not owner of `auth` / `storage` system catalogs.  
3. `COPY auth.users` duplicate key — Auth not replaced.  
4. `DROP SCHEMA public CASCADE` removed PostGIS; geography tables failed until `CREATE EXTENSION postgis`.  
5. `storage.buckets` primary key conflict — historical buckets retained.  
6. `supabase_migrations` accumulated historical versions (148 vs 66).  
7. First schema apply without `SET ROLE postgres` was aborted (permission denied); rerun with `SET ROLE postgres` succeeded for `public`.

## Remediation

- Production Auth disaster: use provider backup/PITR or a **new** project restore that includes Auth — do not expect `psql` COPY into hosted `auth.users` to work.  
- Enable PostGIS (`extensions`) **before** applying a canonical schema dump if `public` was dropped.  
- Storage: separate object backup per canonical bucket; do not treat `storage.objects` as byte recovery.  
- After a drill, keep `wystsapjfpdtoprlmizz` as **SEC-04 RESTORE TEST**. Do not point Android at it. Do not treat it as STAGING.

## Final result

**SEC-04C = PARTIAL** (Auth hosted + Storage bytes abiertos).

Usable entonces: canonical `public` schema, RLS/RPC/security catalogs, VitaCora sequence, data counts.  
No usable entonces como clone: Auth logins, Storage bytes, Edge (hasta redeploy), layout de buckets.

## SEC-04D addendum (2026-09-04)

Owner decisions (Leonardo, not Cursor): PROD **Pro** required; daily backups required; PITR **deferred** for initial launch (~USD 100/month add-on); initial DB RPO ≤ ~24 h subject to PROD verification.

STAGING Dashboard (Leonardo): Free — scheduled backups **NOT AVAILABLE**; PITR **NOT ENABLED**; restore-to-new-project **NOT AVAILABLE**.

Storage drill (read-only STAGING → private `%LOCALAPPDATA%\LeoVer-SEC04D\20260904-storage`):

- buckets confirmed: 5 canonical; 12 objects / 26,880,622 bytes in `public-media`; 4 private buckets empty
- `backup-storage.ps1` + `verify-storage-backup.ps1`: **12/12 SHA-256 OK**
- restore sample to `wystsapjfpdtoprlmizz` prefix `sec04d-probe/` (2 objects, 136,722 and 169,314 bytes): **SHA-256 match**
- STAGING not written; private buckets not made public

Auth: mechanism closed as **Pro physical backup + restore-to-new-project**. Logical dump still does not restore hosted Auth. Fallback documented (re-auth / MFA re-enroll; no backdoors).

**SEC-04 after 04D = PRODUCTION-CONDITIONAL.**

Android: unchanged. Functionality frozen: unchanged. **No commit.**
