# M08 Etapa 3B — Migración 035 validada localmente

**Producto:** LeoVer  
**Rama:** `m08/etapa-3b-migracion-035-local`  
**Archivo:** `supabase/migrations/035_m08_pets_responsibilities_and_rls.sql`  
**Estado:**

```text
M08 ETAPA 3B — MIGRACIÓN 035 VALIDADA LOCALMENTE
STAGING NO AUTORIZADO
REQUIERE ETAPA 4 — REPOSITORIOS Y COMPATIBILIDAD LEGACY
```

## Bloqueo de despliegue

035 **no** debe aplicarse a staging/remoto hasta Etapa 4.

Motivo: el cliente Android legacy (`SupabasePetRepository`) escribe `public.pets` / `owner_id` directamente. 035:

- revoca `INSERT/UPDATE/DELETE` de `authenticated` sobre `pets` y satélites;
- reemplaza el SELECT-all inseguro;
- actualiza `owner_id` solo vía RPC `m08_*` / `_m08_sync_owner_id_from_principal`.

No se crearon bypasses temporales inseguros.

## Contenido de 035

### Tablas

- `public.pet_responsibilities`
- `public.pet_authorizations`
- `public.pet_transfers`
- `public.pet_status_history`

### Columnas nuevas en `public.pets`

- `status` (`ACTIVE`|`DECEASED`|`ARCHIVED`)
- `deceased_at`, `archived_at`
- `microchip_normalized`
- `avatar_file_asset_id` → `file_assets` ON DELETE SET NULL
- `owner_id` nullable (proyección legacy; FK `users` ON DELETE SET NULL)

Conservadas: `photo_url`, health legacy, etc.

### Decisiones clave

| Tema | Implementación |
|---|---|
| Principal | Exactamente uno ACTIVE; XOR persona/org |
| `owner_id` | Proyección; guard por `current_user ∈ {authenticated,anon}` + REVOKE writes; **sin GUC falsificable** |
| Microchip | `m08_normalize_microchip`; unique parcial ACTIVE; aborta backfill si hay dups |
| Capabilities | `text[]` + allowlist; grant deniega ownership caps |
| Transferencias | PENDING → ACCEPTED\|REJECTED\|CANCELLED\|EXPIRED; una PENDING/pet |
| RLS | SELECT vía `m08_actor_can_read_pet` / caps; mutaciones solo RPC |
| Grants | `REVOKE EXECUTE FROM PUBLIC`; expire solo `service_role` |
| M02 | 14 `pet.*`; ADMIN `read`+`view_history`; SUPERADMIN todos |
| M07 | 16 keys `m08.pet.*` (catálogo; sin writes de evento obligatorios en RPC v1) |
| M06 | Hook conceptual categoría PET; sin envío real |

### Diferencias vs freeze 3A

1. Nombre de archivo: `035_m08_pets_responsibilities_and_rls.sql` (usuario Etapa 3B) vs tentativo `…_foundation.sql`.
2. Guard `owner_id`: `current_user` + REVOKE (no GUC `m08.rpc` falsificable por cliente).
3. CHECK `ends_at >= starts_at` (permite cierre en el mismo instante de reloj).
4. Eventos M07 sembrados en catálogo; emisión runtime diferida (no rompe gates M07 de 118 keys en 029+031).

### Scripts

- `scripts/sql/m08_validate_local_035.sql`
- `scripts/ci/m08_stage3b_quality_checks.sh`
- `scripts/ci/m08_stage3_freeze_quality_checks.sh` — acepta max 034 **o** 035 post-3B

### Rollback conceptual

Forward-fix preferido. No down migration destructiva. Revertir remoto solo con plan explícito tras Etapa 4.

## Siguiente paso

**Etapa 4** — adaptar repositorios Android / compatibilidad legacy; luego autorizar staging.
