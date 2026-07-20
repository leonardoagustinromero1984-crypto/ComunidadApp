# M08 Etapa 3C â€” Forward-fix 036 (local)

**Producto:** LeoVer
**Rama:** `m08/etapa-3c-forward-fix-036-local`
**Base:** `1f28422` (Etapa 4A) + 035 `e31c838` + main CI `ebd9487`

```text
M08 ETAPA 3C â€” FORWARD-FIX 036 VALIDADO LOCALMENTE
STAGING NO AUTORIZADO
REQUIERE ETAPA 4B â€” REPOSITORIOS Y ADAPTADOR LEGACY
```

## Archivo

`supabase/migrations/036_m08_pet_repository_compatibility_rpcs.sql`

Complementa 035 **sin** reescribirla. **Sin** columnas nuevas. **Sin** `birth_date`. **Sin** `m08_list_public_profile_pets`.

## RPCs

| RPC | Cap / regla | Retorno |
|---|---|---|
| `m08_update_pet_profile` | `pet.update`; solo ACTIVE; bloque perfil | `public.pets` |
| `m08_update_pet_health` | `pet.manage_health`; solo ACTIVE; bloque salud | `public.pets` |
| `m08_get_pet_access_context` | auth; NONE sin leak si no legible | fila plana |
| `m08_list_accessible_pets` | auth; `m08_actor_can_read_pet`; filtro status allowlist | tabla plana PetRow+contexto |

### Profile fields (reales)

name, species, breed, sex, size, description, age_years, age_months, color, microchip_id (+ `microchip_normalized`).
**No** toca: owner_id, status, deceased_at, archived_at, avatar, photo_url, salud.

### Health fields (reales)

vaccinations, reminders, last_deworming, deworming_product, last_flea_treatment, flea_treatment_product, sterilized, last_vet_visit, health_notes, weight_kg.

### Access context

relation_code precedence: PRINCIPAL â†’ CO_RESPONSIBLE â†’ TEMPORARY_CUSTODIAN â†’ AUTHORIZED â†’ STAFF â†’ NONE.
Capabilities ordenadas sin duplicados vÃ­a allowlist 035. Flags can_* derivadas.

## Seguridad

- SECURITY DEFINER + `search_path = public`
- REVOKE PUBLIC / anon; GRANT authenticated (+ service_role)
- Helpers `_m08_actor_*` no ejecutables por cliente
- Sin GUC falsificable; sin SELECT-all; sin reactivar INSERT/UPDATE/DELETE directos

## Perfil pÃºblico

Mascotas ajenas **ocultas** por defecto. Vitrina pÃºblica = decisiÃ³n futura de producto/privacidad. No bloquea 4B.

## Deuda

- EmisiÃ³n runtime `m08.pet.updated` diferida (catÃ¡logo 035 presente) â€” BACKLOG M07
- Hooks M06 reales â€” BACKLOG previo

## Diferencias vs freeze 4A

- `birth_date` no existe â†’ ages
- `m08_list_accessible_pets` implementada
- `m08_list_public_profile_pets` **no** creada (decisiÃ³n privacidad)

## Staging

**NO autorizado.** Apply remoto 035/036 solo tras Etapa 4B + autorizaciÃ³n explÃ­cita.

## Siguiente paso

**Etapa 4B** â€” adaptar `PetRepository` / DTOs Android a RPC + SELECT RLS. No modificar UI de mÃ¡s allÃ¡ del adapter salvo lo necesario.
