# M08 — Matriz operaciones Android ↔ RPC 035

**Producto:** LeoVer  
**Etapa:** 4A (auditoría)  
**Migración de referencia:** `035_m08_pets_responsibilities_and_rls.sql`  
**Conclusión:** ver Etapa 4A — **requiere 036** para update perfil/salud.

Estados: `CUBIERTO` | `REQUIERE ADAPTADOR` | `CONTRATO SQL FALTANTE` | `BLOQUEANTE` | `FUERA DE ALCANCE`

---

## Matriz operativa

| # | Operación | Método Kotlin | Pantalla / VM | Query / API actual | Contrato SQL 035 | Estado | Prueba unitaria | Integración local | Smoke staging futuro | No regresión |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Listar accesibles | `fetchPets` / nuevo list | — | `SELECT pets` | RLS `m08_actor_can_read_pet` | **REQUIERE ADAPTADOR** | filter RLS mock | list con multi-resp | list post-035 | MyPets count |
| 2 | Mis mascotas | `observePetsForOwner` | MyPets | `WHERE owner_id=` | RLS + filtro owner **incorrecto** | **REQUIERE ADAPTADOR** | listAccessible | seed multi-rol | MyPets | Profile link |
| 3 | Obtener por ID | `getPet` / `observePet` | Detail/Form | `SELECT id=` | RLS SELECT | **CUBIERTO** (+ DTO) | decode row | get id | open detail | |
| 4 | Crear | `createPet` → insert | PetForm add | `INSERT pets` | **REVOKE** → `m08_create_pet_with_principal` | **REQUIERE ADAPTADOR** | RPC params | create local | create staging | |
| 5 | Editar básicos | `updatePet` | PetForm edit | `UPDATE pets` | **REVOKE** / **sin RPC** | **CONTRATO SQL FALTANTE / BLOQUEANTE** | — | — | — | |
| 6 | Actualizar microchip | vía `updatePet` | PetForm | `UPDATE microchip_id` | sin RPC update | **BLOQUEANTE** | — | unique ACTIVE | | |
| 7 | Actualizar salud | vía `updatePet` | PetForm health | `UPDATE` health cols | sin RPC update | **BLOQUEANTE** | — | | | |
| 8 | Eliminar legacy | `deletePet` | Detail/Form | `DELETE` | **REVOKE**; no hard-delete | **REQUIERE ADAPTADOR** → archive | archive maps delete | | | |
| 9 | Archivar | — | — | — | `m08_archive_pet` | **CUBIERTO** (sin UI) | RPC | | | |
| 10 | Restaurar | — | — | — | `m08_restore_pet` | **CUBIERTO** (sin UI) | conflict chip | | | |
| 11 | Fallecimiento | — | — | — | `m08_mark_pet_deceased` | **CUBIERTO** (sin UI) | | | | |
| 12 | Cambiar avatar | upload + `updatePet` | PetForm | storage + UPDATE photo_url | `m08_set_pet_avatar_asset` | **REQUIERE ADAPTADOR** | purpose PET_AVATAR | | | |
| 13 | Galería | — | — | — | M05 PET_GALLERY (no M08 RPC) | **FUERA DE ALCANCE** 4B | | | | |
| 14 | Validar duplicados | — | — | — | `m08_detect_pet_duplicate_candidates` | **CUBIERTO** (sin UI) | | | | |
| 15 | Mascotas en perfil | filter ownerId | Profile | observe + filter | RLS + filtro | **REQUIERE ADAPTADOR** | | | | |
| 16 | Abrir detalle | nav + observe | PetDetail | SELECT id | RLS | **CUBIERTO** | | | | |
| 17 | Filtrar por owner_id | varios | Profile/MyPets | eq owner_id | proyección legacy | **REQUIERE ADAPTADOR** (dejar de usar como ACL) | | | | |
| 18 | Principal organización | — | — | — | create/transfer org XOR | **REQUIERE ADAPTADOR** (`ownerId` null) | | | | |
| 19 | Co-responsable | — | — | — | assign/revoke + caps | **FUERA DE ALCANCE** UI 4B | | | | |
| 20 | Autorizado | — | — | — | grant/revoke caps | **FUERA DE ALCANCE** UI 4B | | | | |

---

## RPC 035 — firmas (auditoría)

| RPC | Args principales | Retorno | Grant | Auth | Capability | Tablas | Errores típicos |
|---|---|---|---|---|---|---|---|
| `m08_create_pet_with_principal` | name, species, sex, size, description, org?, microchip? | `pets` | authenticated | uid | create/self | pets, responsibilities, history | NOT_AUTHENTICATED, FORBIDDEN, PET_NAME_REQUIRED |
| `m08_assign_pet_responsibility` | pet, role, person/org, ends? | responsibility | authenticated | uid | manage_responsibilities | responsibilities | FORBIDDEN, PET_NOT_ACTIVE, XOR, DUPLICATE |
| `m08_revoke_pet_responsibility` | id | responsibility | authenticated | uid | manage_responsibilities | responsibilities | PRINCIPAL_REVOKE_FORBIDDEN |
| `m08_grant_pet_authorization` | pet, person, caps[], until? | authorization | authenticated | uid | manage_authorizations | authorizations | CAPABILITIES_INVALID |
| `m08_revoke_pet_authorization` | id | authorization | authenticated | uid | manage_authorizations | authorizations | |
| `m08_initiate_pet_transfer` | pet, to person/org, expires? | transfer | authenticated | uid | initiate_transfer | transfers | PENDING_EXISTS, SAME_PRINCIPAL, DECEASED/ARCHIVED |
| `m08_accept_pet_transfer` | transfer_id | transfer | authenticated | destino/staff | accept | responsibilities, pets.owner_id | NOT_PENDING, EXPIRED |
| `m08_reject_pet_transfer` | transfer_id | transfer | authenticated | destino | | transfers | |
| `m08_cancel_pet_transfer` | transfer_id, reason? | transfer | authenticated | requester/cancel cap | | transfers | |
| `m08_expire_pet_transfers` | — | int | **service_role** | job | | transfers | FORBIDDEN clientes |
| `m08_mark_pet_deceased` | pet, reason? | pets | authenticated | uid | mark_deceased | pets, auths, transfers, history | ALREADY_DECEASED |
| `m08_archive_pet` | pet, reason? | pets | authenticated | uid | archive | pets, transfers, history | DECEASED_CANNOT_ARCHIVE |
| `m08_restore_pet` | pet | pets | authenticated | uid | restore | pets, history | DECEASED_CANNOT_RESTORE, MICROCHIP conflict |
| `m08_detect_pet_duplicate_candidates` | microchip?, name? | (pet_id, reason) | authenticated | uid | can_read | pets (read) | |
| `m08_set_pet_avatar_asset` | pet, asset_id | pets | authenticated | uid | manage_media | pets | PURPOSE_INVALID |

**Ausente (bloquea 4B CRUD):** cualquier `m08_update_pet_*`.

---

## Campos / DTO

| Necesidad | Estado |
|---|---|
| `PetRow.status`, timestamps lifecycle, `avatar_file_asset_id`, `microchip_normalized` | Falta en Kotlin |
| `ownerId` nullable | Falta |
| Params serializables por RPC | Falta |
| Error mapper `PET_*` / `FORBIDDEN` | Falta |
| Mapper aggregate domain | Falta implementación |

---

## Pruebas futuras (post-3C / 4B)

- Unit: adapter create/update/archive; owner null; list without owner filter.  
- Local: contra Supabase con 035+036.  
- Staging smoke: solo tras Android 4B + SQL apply autorizado.  
- No regresión: MyPets/PetForm/PetDetail flujos felices.

---

## Condición staging

**NO autorizado** hasta:

1. Etapa **3C** (036) validada local;  
2. Etapa **4B** adapter/tests PASS;  
3. Apply remoto explícito 035+036.
