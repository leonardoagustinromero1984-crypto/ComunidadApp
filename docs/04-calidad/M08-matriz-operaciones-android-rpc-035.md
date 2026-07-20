# M08 — Matriz operaciones Android ↔ RPC 035/036 (post-4B)

**Producto:** LeoVer
**Etapa:** 4B adapter listo localmente
**Conclusión:**

```text
M08 ETAPA 4B — REPOSITORIOS Y ADAPTADOR LEGACY LISTOS LOCALMENTE
STAGING NO AUTORIZADO
REQUIERE ETAPA 4C — INTEGRACIÓN LOCAL Y SMOKE APK
```

Estados: `CUBIERTO` | `REQUIERE ADAPTADOR ANDROID` | `FUERA DE ALCANCE` | `PERFIL PÚBLICO OCULTO (decisión)`

---

## Matriz

| Operación | Método / UI | Contrato SQL | Estado |
|---|---|---|---|
| Listar mis mascotas | `observePetsForOwner` | `m08_list_accessible_pets` | **CUBIERTO** (4B adapter) |
| Perfil propio | ProfileVM | list/context + RLS | **CUBIERTO** (4B adapter) |
| Perfil público | UserPublicProfile | ocultar ajenas | **PERFIL PÚBLICO OCULTO (decisión)** |
| Get by id | `getPet` / SELECT | SELECT RLS | **CUBIERTO** (4B DTO) |
| Create | `createPet` | `m08_create_pet_with_principal` | **CUBIERTO** (4B adapter) |
| Update básicos | `updatePet` | `m08_update_pet_profile` | **CUBIERTO** (4B adapter) |
| Update salud | `updatePet` | `m08_update_pet_health` | **CUBIERTO** (4B adapter) |
| Microchip | vía profile RPC | soft-unique ACTIVE | **CUBIERTO** (4B adapter) |
| Delete / archive | `deletePet` | `m08_archive_pet` | **CUBIERTO** (4B adapter) |
| Deceased | domain repo | `m08_mark_pet_deceased` | **CUBIERTO** SQL + domain |
| Restore | domain repo | `m08_restore_pet` | **CUBIERTO** SQL + domain |
| Avatar | `setPetAvatarAsset` | `m08_set_pet_avatar_asset` | **CUBIERTO** (4B adapter) |
| Galería | — | M05 | **FUERA DE ALCANCE** |
| Capacidades | `getPetAccessContext` | `m08_get_pet_access_context` | **CUBIERTO** (4B adapter) |
| Principal org | ownerId null | owner_id null | **CUBIERTO** (4B adapter) |
| Co-responsable / autorizado UI | domain repos | RPC 035 | **CUBIERTO** repos / **FUERA DE ALCANCE** UI |

## Contratos 036 (implementados)

1. `m08_update_pet_profile` — `pet.update`
2. `m08_update_pet_health` — `pet.manage_health`
3. `m08_get_pet_access_context`
4. `m08_list_accessible_pets`
5. `m08_list_public_profile_pets` — **no creada**; ocultar por defecto

## Condición staging

**NO autorizado** hasta Etapa 4C (integración/smoke) + apply explícito 035+036.
