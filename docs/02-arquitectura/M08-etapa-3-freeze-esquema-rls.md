# M08 Etapa 3A — Freeze de esquema y RLS

**Producto:** LeoVer  
**Rama:** `m08/etapa-3-freeze-esquema-rls`  
**Fecha:** 2026-07-19  
**Base Etapa 2:** `d9547ac`  
**Tipo:** documental Etapa 3A; **SQL ejecutable creado en Etapa 3B** (`035_m08_pets_responsibilities_and_rls.sql`).

```text
M08 ETAPA 3A — FREEZE DE ESQUEMA Y RLS APROBABLE
M08 ETAPA 3B — MIGRACIÓN 035 VALIDADA LOCALMENTE (ver M08-etapa-3b-migracion-035-local.md)
STAGING NO AUTORIZADO — REQUIERE ETAPA 4
```

**Prohibido en 3A:** crear archivo bajo `supabase/migrations/`.
**3B:** creó y validó localmente `supabase/migrations/035_m08_pets_responsibilities_and_rls.sql` — **sin** apply remoto.

---

## 2. Fuentes revisadas

| Fuente | Hallazgo relevante |
|---|---|
| `001` pets + RLS | SELECT abierto; CUD `owner_id = auth.uid()`; sin CHECKs/status |
| `003`/`005` | Columnas salud/perfil; sin custody |
| `012` | Única FK a pets: `pet_clinical_records.pet_id` CASCADE |
| `018`/`031` | `permissions` + `on conflict do nothing`; `has_permission` DEFINER |
| `019` | Membresías org; XOR/partial unique ACTIVE; historial append vía RPC |
| `024` | `PET_AVATAR`/`PET_GALLERY`; `resource_type` PET; soft-delete files |
| `026` | Categoría notif `PET` ya en allowlist función |
| `029`/`031` | Catálogo eventos `mNN.*` con insert idempotente |
| `034` | Internos `_…` sin EXECUTE anon/authenticated |
| Domain Etapa 2 | `PetCapability`, estados, transfer machine |

Convención de nombres: **`public.m08_*`** (helpers/RPC), **`public._m08_*`** (internos service_role). Evitar namespace suelto `pet_*` en funciones.

---

## 3. Decisiones congeladas

| ID | Decisión |
|---|---|
| F1 | Tablas satélite: `pet_responsibilities`, `pet_authorizations`, `pet_transfers`, `pet_status_history` |
| F2 | Extender `pets` con status/lifecycle + microchip_normalized + avatar_file_asset_id; **no drop** legacy |
| F3 | Principal: columnas XOR `person_id` / `organization_id` (como owner_kind M05) |
| F4 | Capacidades: `text[]` + CHECK `= any(m08_pet_capability_codes())` |
| F5 | **`owner_id` solo vía RPC DEFINER**; trigger bloquea escritura directa divergente |
| F6 | Soft-unique microchip: índice parcial `WHERE microchip_normalized IS NOT NULL AND status = 'ACTIVE'` |
| F7 | RLS rewrite pets (fin SELECT abierto); historial/transfers sin write cliente |
| F8 | Event keys `m08.*`; notifs reutilizan categoría M06 `PET` |
| F9 | Seed permisos = códigos Kotlin `pet.*` ya existentes |
| F10 | Número de archivo: **035** si freeze aprobado; no crear hasta Etapa 3B |

---

## 4. Resumen tablas / owner_id / microchip

Detalle DDL conceptual: `M08-esquema-propuesto-migracion-035.md`.  
Matriz RLS: `M08-matriz-rls-y-permisos.md`.  
Validación: `M08-plan-validacion-migracion-035.md`.

**owner_id (F5):** proyección legacy. Persona principal ⇒ `owner_id = person_id`. Org pura ⇒ `owner_id NULL`. Mutación solo vía RPC DEFINER (`m08_create_pet_with_principal`, `m08_accept_pet_transfer`, `_m08_sync_owner_id_from_principal`) y backfill. Trigger `_m08_pets_guard_owner_id` BEFORE UPDATE OF owner_id: **bloquea si `current_user ∈ {authenticated, anon}`**; RPCs DEFINER actualizan como owner de tabla. Complementado con `REVOKE INSERT/UPDATE/DELETE` a authenticated/anon. **No** se usa GUC `m08.rpc` (falsificable por cliente).

**microchip (F6):** normalización = trim, quitar `[\s\-_]`, uppercase (igual Etapa 2). Vacío → NULL. Índice único parcial solo ACTIVE. DECEASED/ARCHIVED no bloquean reuso de chip. Pre-backfill reporta duplicados ACTIVE; 035 falla o deja índice diferido según plan de validación.

---

## 5. RLS / RPC / backfill / permisos / eventos

- Helpers DEFINER `m08_actor_can_read_pet`, `m08_actor_has_capability`, etc.; `search_path = public`; REVOKE PUBLIC; GRANT authenticated solo donde sea predicate-safe.
- RPCs cliente: GRANT authenticated; internos: service_role.
- Backfill: 1 PRINCIPAL por `owner_id` no null; idempotente; reportar `owner_id` null legacy.
- Permisos: insert `permissions` + link ADMIN/SUPERADMIN según matriz Etapa 2 (ADMIN: read+view_history; SUPERADMIN: set amplio).
- Eventos: `m08.pet.created`, `m08.pet.transfer.accepted`, … (lista completa en esquema propuesto).
- Notifs: plantillas conceptuales sobre categoría `PET` (sin enqueue simulado en 035 salvo hooks mínimos documentados).

---

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| Romper clientes que asumen SELECT-all pets | Staging + Android smoke; policies dual-read durante transición documentada |
| Duplicados microchip legacy | Precheck + reporte; no índice hasta limpio o exclusión explícita |
| Recursión trigger owner_id | `current_user` + REVOKE writes (sin GUC) |
| CASCADE clínica al DELETE | Preferir ARCHIVE/DECEASED; restringir DELETE RLS |
| Org principal sin membership | RPC exige org operativa + permiso org |

---

## 7. Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| `owner_id` sync trigger automático | Recursión y writes opacos; contradice patrón org “solo RPC” |
| Columna GENERATED owner_id | PG generated no puede depender fácilmente de satélite |
| Capabilities en jsonb libre | Menos validable que text[] + allowlist M06-style |
| Principal como `actor_type`+`actor_id` text | Peor FK tipada; XOR uuid preferido |
| Namespace funciones `pet_*` | Rompe convención `m0N_` |

---

## 8. Condiciones para crear 035 (Etapa 3B)

1. Este freeze aprobado.  
2. Sin conflictos con `main` / 034.  
3. Plan de validación aceptado.  
4. Estrategia de duplicados microchip acordada (fail-closed vs quarantine).  
5. Autorización explícita para escribir `supabase/migrations/035_…sql`.

---

## 9. Estado

```text
M08 ETAPA 3A — FREEZE DE ESQUEMA Y RLS APROBABLE
Migración 035 creada: NO
SQL ejecutable: NO
Etapa 3B: NO iniciada
```
