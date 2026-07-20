# M08 — Esquema propuesto migración 035 (conceptual)

**Producto:** LeoVer  
**Freeze:** Etapa 3A  
**Archivo futuro tentativo:** `supabase/migrations/035_m08_pets_responsibilities_foundation.sql`  
**Estado:** **NO CREADO** — este documento no es SQL ejecutable.

Todos los nombres son propuestas alineadas a `m08_*` / patrones 018–034.

---

## 1. Extensiones / tipos

- Reutilizar `gen_random_uuid()` (pgcrypto ya en 001).  
- Sin ENUM PG nativos: `text` + CHECK (patrón org/M06).  
- Helper allowlist:

```text
public.m08_pet_lifecycle_statuses() → text[]
  {'ACTIVE','DECEASED','ARCHIVED'}

public.m08_pet_responsibility_roles() → text[]
  {'PRINCIPAL','CO_RESPONSIBLE','TEMPORARY_CUSTODIAN'}

public.m08_pet_link_statuses() → text[]
  {'ACTIVE','PENDING_ACCEPTANCE','REVOKED','EXPIRED','SUPERSEDED'}

public.m08_pet_transfer_statuses() → text[]
  {'PENDING','ACCEPTED','REJECTED','CANCELLED','EXPIRED'}

public.m08_pet_capability_codes() → text[]
  -- mismos códigos que PetCapability / PermissionCode.PET_*
```

---

## 2. ALTER `public.pets` (sin DROP legacy)

| Columna nueva | Tipo | Notas |
|---|---|---|
| `status` | text NOT NULL DEFAULT `'ACTIVE'` | CHECK ∈ lifecycle |
| `deceased_at` | timestamptz NULL | obligatorio si DECEASED (CHECK o RPC) |
| `archived_at` | timestamptz NULL | si ARCHIVED |
| `microchip_normalized` | text NULL | ver §5 |
| `avatar_file_asset_id` | uuid NULL FK → `file_assets(id)` SET NULL | M05 canónico |
| `owner_id` | **ALTER a NULL** | deja de ser NOT NULL; FK users ON DELETE SET NULL o CASCADE a evaluar (preferir SET NULL si org pura) |

Conservar: `photo_url`, health json/text, breed, etc.

CHECKs nuevos:

- `pets_status_allowed`
- `pets_deceased_ts_chk` (status=DECEASED ⇒ deceased_at NOT NULL)
- `pets_archived_ts_chk` (status=ARCHIVED ⇒ archived_at NOT NULL)

Índices:

- `pets_status_created_at_idx (status, created_at desc)`
- Soft-unique microchip:  
  `pets_microchip_active_uniq ON (microchip_normalized) WHERE microchip_normalized IS NOT NULL AND status = 'ACTIVE'`

`updated_at`: seguir bump manual en RPC (no introducir trigger genérico nuevo en 035 salvo necesidad).

---

## 3. `public.pet_responsibilities`

| Columna | Tipo |
|---|---|
| `id` | uuid PK default gen_random_uuid() |
| `pet_id` | uuid NOT NULL FK pets ON DELETE RESTRICT |
| `role_code` | text NOT NULL CHECK ∈ roles |
| `person_id` | uuid NULL FK auth.users/users |
| `organization_id` | uuid NULL FK organizations |
| `status` | text NOT NULL CHECK ∈ link statuses |
| `starts_at` | timestamptz NOT NULL |
| `ends_at` | timestamptz NULL |
| `revoked_at` | timestamptz NULL |
| `revoked_by` | uuid NULL |
| `created_by` | uuid NOT NULL |
| `created_at` | timestamptz NOT NULL default utc now() |
| `accepted_at` | timestamptz NULL |
| `reason` | text NULL (sanitizado; opcional corto) |

Constraints:

- XOR actor: `(person_id IS NOT NULL) <> (organization_id IS NOT NULL)` — exactamente uno.  
- TEMPORARY_CUSTODIAN ⇒ `ends_at IS NOT NULL` AND `ends_at > starts_at`.  
- PRINCIPAL ACTIVE ⇒ person u org según holder.

Índices:

- `pet_responsibilities_one_principal_active_uniq (pet_id) WHERE role_code='PRINCIPAL' AND status='ACTIVE'`  
- `pet_responsibilities_actor_role_active_uniq (pet_id, role_code, coalesce(person_id,'000…'), coalesce(organization_id,'000…')) WHERE status='ACTIVE'`  
  (o dos índices parciales person/org)  
- `(pet_id, created_at desc)`

Historial: **no DELETE** físico; REVOKE/SUPERSEDE.

Mecanismo unicidad principal: **índice único parcial** (no solo RPC).

---

## 4. `public.pet_authorizations`

| Columna | Tipo |
|---|---|
| `id` | uuid PK |
| `pet_id` | uuid FK pets RESTRICT |
| `person_id` | uuid NOT NULL |
| `granted_by` | uuid NOT NULL |
| `capabilities` | text[] NOT NULL |
| `status` | text NOT NULL |
| `valid_from` | timestamptz NOT NULL |
| `valid_until` | timestamptz NULL |
| `revoked_at` / `revoked_by` | nullable |
| `created_at` | timestamptz NOT NULL |
| `accepted_at` | timestamptz NULL |

Constraints:

- `capabilities <> '{}'`  
- `CHECK (capabilities <@ public.m08_pet_capability_codes())` o trigger que valida cada elemento ∈ allowlist  
- Prohibir en CHECK/trigger capacidades: `pet.initiate_transfer`, `pet.mark_deceased`, `pet.archive`, `pet.manage_responsibilities` (alineado dominio Etapa 2)

**Representación capabilities elegida: `text[]` + allowlist function** — igual patrón `m06_notification_categories()`; más simple que tabla N:N para v1; jsonb libre descartado.

Índice parcial activo: `(pet_id, person_id) WHERE status='ACTIVE'` (no único estricto si se permiten múltiples filas con caps distintas; opcional unique on pet+person+status ACTIVE y merge caps en RPC).

Freeze v1: **una fila ACTIVE por (pet_id, person_id)**; grant reemplaza/amplía vía RPC (supersede anterior).

---

## 5. `public.pet_transfers`

Representación principal origen/destino: **columnas tipadas XOR** (no `actor_type` text).

| Columna | Tipo |
|---|---|
| `id` | uuid PK |
| `pet_id` | uuid FK |
| `from_person_id` / `from_organization_id` | XOR |
| `to_person_id` / `to_organization_id` | XOR |
| `status` | text CHECK transfer statuses |
| `requested_by` | uuid NOT NULL |
| `requested_at` | timestamptz NOT NULL |
| `expires_at` | timestamptz NOT NULL |
| `responded_at` / `responded_by` | nullable |
| `cancelled_at` | nullable |
| `cancellation_reason` | text NULL |
| `correlation_id` | text NULL |
| `created_at` | timestamptz NOT NULL |

Índice: `pet_transfers_one_pending_uniq (pet_id) WHERE status='PENDING'`.

CHECKs: from ≠ to (comparar pares); expires_at > requested_at.

Aceptación: **solo RPC** transaccional (supersede PRINCIPAL, update pets.owner_id/status projection, history, events).

---

## 6. `public.pet_status_history`

Append-only.

| Columna | Tipo |
|---|---|
| `id` | uuid PK |
| `pet_id` | uuid FK |
| `previous_status` | text NULL |
| `new_status` | text NOT NULL |
| `changed_by` | uuid NOT NULL |
| `changed_at` | timestamptz NOT NULL |
| `reason` | text NULL |
| `metadata` | jsonb NOT NULL default `{}` (allowlist keys en writer) |
| `correlation_id` | text NULL |

RLS: SELECT según can_read; **sin** INSERT/UPDATE/DELETE para authenticated (solo RPC DEFINER).

---

## 7. Triggers conceptuales

| Nombre | Momento | Comportamiento |
|---|---|---|
| `_m08_pets_guard_owner_id` | BEFORE UPDATE OF owner_id ON pets | Si `owner_id` cambia y `current_setting('m08.rpc', true) <> '1'` → `raise exception 'PET_OWNER_ID_DIRECT_FORBIDDEN'` |
| (opcional) `_m08_pet_auth_caps_check` | BEFORE INSERT/UPDATE ON pet_authorizations | Validar caps ⊂ allowlist y denylist ownership |

RPCs setean `perform set_config('m08.rpc','1', true);` al inicio.

---

## 8. Helpers SQL (conceptual)

Todos `SECURITY DEFINER`, `set search_path = public`, VOLATILE/STABLE según corresponda.

| Función | Rol |
|---|---|
| `m08_pet_capability_codes()` | allowlist |
| `m08_normalize_microchip(text)` | IMMUTABLE SQL |
| `m08_current_principal(pet_id)` | lee responsibility ACTIVE PRINCIPAL |
| `m08_actor_has_responsibility(pet_id, role?)` | |
| `m08_actor_has_capability(pet_id, capability)` | roles defaults ∪ auth ∪ has_permission staff |
| `m08_actor_can_read_pet(pet_id)` | |
| `m08_actor_can_manage_pet(pet_id)` | |
| `m08_require_capability(pet_id, capability)` | raise FORBIDDEN |
| `_m08_sync_owner_id_from_principal(pet_id)` | interno; setea owner_id + GUC |

Grants: allowlist + can_* → authenticated EXECUTE; `_m08_*` → service_role only; require_* usado dentro de RPC.

---

## 9. RPC (firmas conceptuales)

Patrón: `security definer`, `search_path=public`, `REVOKE FROM public/anon`, `GRANT TO authenticated` (salvo jobs).

| RPC | Params clave | Mutaciones | Cap / auth | Evento M07 |
|---|---|---|---|---|
| `m08_create_pet_with_principal` | name,…, principal person/org | pets+responsibility+history | pet.create | m08.pet.created |
| `m08_assign_pet_responsibility` | pet, role, actor | responsibilities | manage_responsibilities | …assigned |
| `m08_revoke_pet_responsibility` | id | status REVOKED | manage_responsibilities | …revoked |
| `m08_grant_pet_authorization` | pet, person, caps[], until | authorizations | manage_authorizations | …granted |
| `m08_revoke_pet_authorization` | id | REVOKED | manage_authorizations | …revoked |
| `m08_initiate_pet_transfer` | pet, to principal, expires | transfers PENDING | initiate_transfer | …initiated |
| `m08_accept_pet_transfer` | transfer_id | tx: SUPERSEDE+owner_id+history | destino / accept_transfer | …accepted |
| `m08_reject_pet_transfer` | id | REJECTED | destino | …rejected |
| `m08_cancel_pet_transfer` | id | CANCELLED | cancel_transfer / requester | …cancelled |
| `m08_expire_pet_transfers` | now? | EXPIRED batch | service_role job | …expired |
| `m08_mark_pet_deceased` | pet, reason | status+history+revoke auths+cancel pending | mark_deceased | …deceased |
| `m08_archive_pet` / `m08_restore_pet` | pet | status+history | archive/restore | …archived/restored |
| `m08_detect_pet_duplicate_candidates` | chip?, name? | read-only | read | …duplicate_detected (opcional) |
| `m08_set_pet_avatar_asset` | pet, file_asset_id | pets.avatar + validate M05 | manage_media | …avatar_changed |

Idempotencia: transfers accept once; create responsibility on conflict active → error `PET_RESPONSIBILITY_DUPLICATE_ACTIVE`.

Errores dominio (text): `NOT_AUTHENTICATED`, `FORBIDDEN`, `PET_*` alineados Kotlin.

Notificaciones M06: tras accept/assign/grant/revoke/initiate — enqueue conceptual categoría `PET` (implementación envío = deuda M06; 035 puede insertar outbox si patrón m06_enqueue existe y está permitido — freeze: **documentar hook; implementar mínimo en 3B solo si m06_enqueue_domain_event es estable**).

---

## 10. Orden interno migración 035

1. Prechecks (versión 034 aplicada; no 035 previa).  
2. Allowlist functions.  
3. ALTER pets columns + CHECKs (owner_id DROP NOT NULL).  
4. CREATE tables satélite + FKs.  
5. Constraints XOR / temporal.  
6. Índices no-únicos + unique principal/pending (microchip **después** backfill).  
7. Helpers + guard trigger.  
8. Backfill responsibilities + normalize microchip + status ACTIVE.  
9. Validación backfill (counts).  
10. Índice soft-unique microchip (falla si dups).  
11. RPCs.  
12. DROP/REPLACE policies pets; policies satélite.  
13. Grants EXECUTE.  
14. Seed permissions + role_permissions.  
15. Insert observability_event_catalog `on conflict do nothing`.  
16. Comments.  
17. Verificaciones finales (asserts SQL).

Transaccional: un solo `BEGIN/COMMIT` preferible; si microchip dups bloquean, 3B puede partir en 035a (schema+backfill) / 035b (unique) — **freeze prefiere una sola 035 fail-closed** tras limpieza.

---

## 11. Eventos M07 (keys definitivas freeze)

```text
m08.pet.created
m08.pet.updated
m08.pet.responsibility.assigned
m08.pet.responsibility.revoked
m08.pet.authorization.granted
m08.pet.authorization.revoked
m08.pet.transfer.initiated
m08.pet.transfer.accepted
m08.pet.transfer.rejected
m08.pet.transfer.cancelled
m08.pet.transfer.expired
m08.pet.marked_deceased
m08.pet.archived
m08.pet.restored
m08.pet.duplicate_detected
m08.pet.avatar_changed
```

---

## 12. Notificaciones M06 (conceptual)

| Disparador | Categoría | Deep link |
|---|---|---|
| Transfer recibida | PET | PET / pet_id |
| Transfer aceptada/rechazada | PET | PET |
| Responsabilidad asignada | PET | PET |
| Autorización granted/revoked | PET | PET |
| Custodia temporal por vencer | PET | PET |

Sin envío real obligatorio en 035.

---

## 13. Backfill legacy

1. `UPDATE pets SET status='ACTIVE' WHERE status IS NULL` (post-add column).  
2. `microchip_normalized = m08_normalize_microchip(microchip_id)`.  
3. Para cada pet con `owner_id IS NOT NULL`: insert PRINCIPAL ACTIVE person si no existe.  
4. `starts_at = created_at`, `created_by = owner_id`.  
5. Reportar pets con `owner_id IS NULL` pre-migración (no deberían existir hoy).  
6. No tocar photo_url; no crear file_assets desde URL.  
7. No asignar pet_id a adoptions/lost_found.  
8. Insert history inicial `previous_status NULL → ACTIVE` reason `BACKFILL_035` (actor system/service).

Pre: count pets = count future principals.  
Post: assert one principal per pet; no orphan.

---

## 14. Permisos M02 seed

Códigos = `PermissionCode` Etapa 2.  
Idempotente `on conflict do nothing`.

| Rol | Permisos |
|---|---|
| ADMIN | pet.read, pet.view_history |
| SUPERADMIN | todos pet.* del catálogo |
| USER/MODERATOR | ninguno por plataforma (acceso vía grafo) |

---

## 15. Rollback conceptual (no down migration)

- Deshabilitar policies nuevas y restaurar policies 001 solo en emergencia staging.  
- No borrar `pet_*` history si hubo transfers ACCEPTED.  
- Retirar role_permissions pet.*; dejar permissions rows.  
- Drop índice microchip antes de revertir columnas.  
- `owner_id` NOT NULL solo si no hay org principals.  
- Preferir forward-fix.

---

## 16. Aviso

Este archivo **no** debe copiarse ciegamente a `supabase/migrations/`. Etapa **3B** redactará SQL ejecutable a partir de este freeze.
