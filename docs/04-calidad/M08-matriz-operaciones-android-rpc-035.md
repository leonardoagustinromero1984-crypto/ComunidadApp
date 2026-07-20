# M08 — Matriz operaciones Android ↔ RPC 035 (cierre 4A)

**Producto:** LeoVer
**Etapa:** 4A cerrada con bloqueo SQL
**Conclusión:**

```text
M08 ETAPA 4A — BLOQUEADA POR CONTRATOS SQL FALTANTES
REQUIERE ETAPA 3C — FORWARD-FIX 036
```

Estados: `CUBIERTO` | `REQUIERE ADAPTADOR ANDROID` | `CONTRATO SQL FALTANTE` | `BLOQUEANTE` | `FUERA DE ALCANCE` | `DECISIÓN PENDIENTE BLOQUEANTE PARA PERFIL PÚBLICO`

---

## Matriz

| Operación | Método / UI | Contrato 035 | Estado | Prueba unitaria | Integración local | Smoke staging | No regresión |
|---|---|---|---|---|---|---|---|
| Listar mis mascotas | `observePetsForOwner` / MyPets | SELECT RLS; filtro owner incorrecto | **REQUIERE ADAPTADOR ANDROID** (+ list RPC 036) | list sin owner filter | multi-resp | MyPets | count |
| Perfil propio | ProfileVM filter owner | RLS + filter | **REQUIERE ADAPTADOR ANDROID** | | | | |
| Perfil público | UserPublicProfile filter owner | fin SELECT-all | **DECISIÓN PENDIENTE BLOQUEANTE PARA PERFIL PÚBLICO** | | | | |
| Get by id | `getPet` / Detail | SELECT + RLS | **CUBIERTO** (+ DTO) | decode | | open detail | |
| Create | `createPet` insert | `m08_create_pet_with_principal` | **REQUIERE ADAPTADOR ANDROID** | RPC | create | create | |
| Update básicos | `updatePet` | **sin RPC** | **CONTRATO SQL FALTANTE / BLOQUEANTE** → `m08_update_pet_profile` | | | | |
| Update salud | `updatePet` | **sin RPC** | **CONTRATO SQL FALTANTE / BLOQUEANTE** → `m08_update_pet_health` | | | | |
| Microchip | vía update | **sin RPC** | **BLOQUEANTE** (en profile RPC) | conflict ACTIVE | | | |
| Delete / archive | `deletePet` | `m08_archive_pet` | **REQUIERE ADAPTADOR ANDROID** | map delete→archive | | | |
| Deceased | — | `m08_mark_pet_deceased` | **CUBIERTO** (sin UI) | | | | |
| Restore | — | `m08_restore_pet` | **CUBIERTO** (sin UI) | chip conflict | | | |
| Avatar | update photo_url | `m08_set_pet_avatar_asset` | **REQUIERE ADAPTADOR ANDROID** | purpose | | | |
| Galería | — | M05 only | **FUERA DE ALCANCE** | | | | |
| Capacidades / canManage | `ownerId == uid` | helpers; falta contexto | **CONTRATO SQL FALTANTE** → `m08_get_pet_access_context` | | | | |
| Principal organización | — | XOR / owner null | **REQUIERE ADAPTADOR ANDROID** | nullable owner | | | |
| Co-responsable | — | assign/revoke | **FUERA DE ALCANCE** UI 4B | | | | |
| Autorizado | — | grant/revoke | **FUERA DE ALCANCE** UI 4B | | | | |
| Listado accesible + relación | — | SELECT RLS insuficiente para caps | **CONTRATO SQL FALTANTE** → `m08_list_accessible_pets` | | | | |

---

## Contratos 036 congelados (archivo aún no creado)

1. `m08_update_pet_profile` — `pet.update`
2. `m08_update_pet_health` — `pet.manage_health`
3. `m08_get_pet_access_context`
4. `m08_list_accessible_pets`
5. `m08_list_public_profile_pets` — **solo si** se aprueba vitrina pública (recomendación 4A: diferir; ocultar por defecto)

---

## Operaciones cubiertas por 035 (con adaptador Android)

- Create (RPC)
- Get by id (SELECT RLS)
- Archive / restore / deceased (RPC)
- Avatar asset (RPC)
- Transfers / responsibilities / authorizations (RPC, fuera UI 4B)
- Detect duplicates (RPC)

## Bloqueantes sin 036

- Update básicos / salud / microchip
- Access context / capabilities para UI
- Listado accesible enriquecido
- Perfil público (decisión + posible RPC)

---

## Condición staging

**NO autorizado** hasta Etapa 3C (036 local PASS) + Etapa 4B (adapter PASS) + apply explícito 035+036.
