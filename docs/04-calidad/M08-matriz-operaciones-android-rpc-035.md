# M08 â€” Matriz operaciones Android â†” RPC 035/036 (post-3C)

**Producto:** LeoVer
**Etapa:** 3C SQL listo; 4B adapter pendiente
**ConclusiÃ³n:**

```text
M08 ETAPA 3C â€” FORWARD-FIX 036 VALIDADO LOCALMENTE
STAGING NO AUTORIZADO
REQUIERE ETAPA 4B â€” REPOSITORIOS Y ADAPTADOR LEGACY
```

Estados: `CUBIERTO` | `REQUIERE ADAPTADOR ANDROID` | `FUERA DE ALCANCE` | `PERFIL PÃšBLICO OCULTO (decisiÃ³n)`

---

## Matriz

| OperaciÃ³n | MÃ©todo / UI | Contrato SQL | Estado |
|---|---|---|---|
| Listar mis mascotas | `observePetsForOwner` | `m08_list_accessible_pets` | **REQUIERE ADAPTADOR ANDROID** |
| Perfil propio | ProfileVM | list/context + RLS | **REQUIERE ADAPTADOR ANDROID** |
| Perfil pÃºblico | UserPublicProfile | ocultar ajenas | **PERFIL PÃšBLICO OCULTO (decisiÃ³n)** |
| Get by id | `getPet` | SELECT RLS | **REQUIERE ADAPTADOR ANDROID** (DTO) |
| Create | `createPet` | `m08_create_pet_with_principal` | **REQUIERE ADAPTADOR ANDROID** |
| Update bÃ¡sicos | `updatePet` | `m08_update_pet_profile` | **REQUIERE ADAPTADOR ANDROID** |
| Update salud | `updatePet` | `m08_update_pet_health` | **REQUIERE ADAPTADOR ANDROID** |
| Microchip | vÃ­a profile RPC | soft-unique ACTIVE | **REQUIERE ADAPTADOR ANDROID** |
| Delete / archive | `deletePet` | `m08_archive_pet` | **REQUIERE ADAPTADOR ANDROID** |
| Deceased | â€” | `m08_mark_pet_deceased` | **CUBIERTO** SQL (sin UI) |
| Restore | â€” | `m08_restore_pet` | **CUBIERTO** SQL (sin UI) |
| Avatar | photo_url legacy | `m08_set_pet_avatar_asset` | **REQUIERE ADAPTADOR ANDROID** |
| GalerÃ­a | â€” | M05 | **FUERA DE ALCANCE** |
| Capacidades | ownerId==uid | `m08_get_pet_access_context` | **REQUIERE ADAPTADOR ANDROID** |
| Principal org | â€” | owner_id null | **REQUIERE ADAPTADOR ANDROID** |
| Co-responsable / autorizado UI | â€” | RPC 035 | **FUERA DE ALCANCE** UI 4B |

## Contratos 036 (implementados)

1. `m08_update_pet_profile` â€” `pet.update`
2. `m08_update_pet_health` â€” `pet.manage_health`
3. `m08_get_pet_access_context`
4. `m08_list_accessible_pets`
5. `m08_list_public_profile_pets` â€” **no creada**; ocultar por defecto

## CondiciÃ³n staging

**NO autorizado** hasta Etapa 4B (adapter PASS) + apply explÃ­cito 035+036.
