# M08 Etapa 4A — Auditoría de compatibilidad de repositorios Android

**Producto:** LeoVer  
**Rama:** `m08/etapa-4a-auditoria-compatibilidad-repositorios`  
**Tipo:** auditoría y freeze documental (sin implementación de repositorios)  
**Base 3B:** `e31c838`  
**Main integrado:** `ebd9487` (PR #2 CI M07)  
**Merge sync:** `chore(m08): sync main CI fix before stage 4`

```text
M08 ETAPA 4A — BLOQUEADA POR CONTRATOS SQL FALTANTES
REQUIERE ETAPA 3C — FORWARD-FIX 036
STAGING NO AUTORIZADO
```

---

## 1. Base integrada

| Ref | SHA / nota |
|---|---|
| Cierre 3B | `e31c838` — migración 035 local |
| CI fix | `a705f25`, `7105a11` → merge `ebd9487` |
| Highest migration | **035** |
| 036 | **ausente** |
| Android / UI en esta etapa | **sin cambios de código** |
| Supabase remoto | **no modificado** |

Quality gates en esta rama: M07 acepta max 035; M08 stage2/3A/3B PASS (ajuste stage3b: no auto-escanear el propio script por tokens `--linked`).

---

## 2. Repositorios y flujos legacy actuales

### Contratos

| Pieza | Ubicación | Rol |
|---|---|---|
| `PetRepository` | `data/repository/PetRepository.kt` | Interfaz legacy observe/CRUD |
| `MockPetRepository` | mismo archivo | InMemory |
| `SupabasePetRepository` | `data/repository/SupabaseRepositories.kt` | Delega a `PetSupabaseDataSource` |
| `PetSupabaseDataSource` | `data/remote/supabase/SupabaseDataSources.kt` | Postgrest `from("pets")` |
| `data.model.Pet` / `PetRow` | model + `SupabaseMappers.kt` | `ownerId` **non-null**; sin status/avatar M08 |
| Domain Etapa 2 | `domain/pets/*` | Contratos sin implementación Supabase |
| `DataProvider.petRepository` | `DataProvider.kt` | Supabase vs Mock |

### Flujos UI

| Pantalla / VM | Operación |
|---|---|
| `MyPetsViewModel` | `observePetsForOwner(authUser.id)` |
| `ProfileViewModel` | `observePets()` + filter `ownerId == auth` |
| `UserPublicProfileViewModel` | filter `ownerId == userId` |
| `PetFormViewModel` | create/update insert/update row; foto M05 + `updatePet(photoUrl=assetId)` |
| `PetDetailViewModel` | observe + `deletePet`; `canManage = ownerId == userId` |
| Nav | `MY_PETS`, `ADD_PET`, `EDIT_PET`, `PET_DETAIL` |

### Writes legacy (bloqueados tras apply 035)

- `INSERT` pets con `owner_id`
- `UPDATE` pets (fila completa, incluye `owner_id`)
- `DELETE` pets

035: `REVOKE INSERT/UPDATE/DELETE` + trigger `PET_OWNER_ID_DIRECT_FORBIDDEN` vía `current_user` (no GUC).

---

## 3. Impacto RLS 035 en lecturas

| Consulta | Bajo 035 |
|---|---|
| `SELECT` por `id` | OK si `m08_actor_can_read_pet` |
| `SELECT` sin filtro (fetchPets) | Solo filas legibles por actor |
| `SELECT WHERE owner_id = ?` | Semántica **rota** para co-resp / org principal (`owner_id` null) |
| Filtro Kotlin `pet.ownerId == user` | Igual problema + crash potencial si `owner_id` null y decode non-null |

---

## 4. Cobertura RPC 035 (resumen)

### Disponibles (cliente authenticated salvo expire)

| RPC | Retorno | Cap / auth | Uso Etapa 4 |
|---|---|---|---|
| `m08_create_pet_with_principal` | `pets` | actor / org admin | Reemplaza create |
| `m08_assign/revoke_pet_responsibility` | row | manage_responsibilities | Más allá UI actual |
| `m08_grant/revoke_pet_authorization` | row | manage_authorizations | Más allá UI |
| `m08_initiate/accept/reject/cancel_pet_transfer` | row | transfer caps | Más allá UI |
| `m08_expire_pet_transfers` | int | service_role | Job |
| `m08_mark_pet_deceased` | `pets` | mark_deceased | Lifecycle |
| `m08_archive_pet` / `m08_restore_pet` | `pets` | archive/restore | Reemplaza delete |
| `m08_detect_pet_duplicate_candidates` | setof | can_read | Duplicados |
| `m08_set_pet_avatar_asset` | `pets` | manage_media | Avatar M05 |

Helpers lectura: `m08_actor_can_read_pet`, `m08_actor_has_capability`, `m08_current_principal`, allowlists, `m08_normalize_microchip`.

### Errores distinguibles (texto)

`NOT_AUTHENTICATED`, `FORBIDDEN`, `PET_NOT_FOUND`, `PET_NOT_ACTIVE`, `PET_NAME_REQUIRED`, `PET_OWNER_ID_DIRECT_FORBIDDEN`, `PET_MICROCHIP_ACTIVE_CONFLICT`, `PET_*_TRANSFER_*`, etc.

### Serialización Kotlin

Retorno `public.pets` incluye columnas nuevas (`status`, `deceased_at`, `archived_at`, `microchip_normalized`, `avatar_file_asset_id`, `owner_id` nullable). `PetRow` actual **no** es compatible sin ampliación y `ownerId` nullable.

---

## 5. Verificación crítica de cobertura (PASO 8)

| # | Pregunta | Respuesta |
|---|---|---|
| 1 | ¿RPC update nombre/especie/sexo/descripción/raza/edad? | **NO** |
| 2 | ¿RPC update salud? | **NO** |
| 3 | ¿Listar accesibles por responsabilidad? | **Parcial:** `SELECT` bajo RLS (sin RPC dedicada) — suficiente si se deja de filtrar por `owner_id` |
| 4 | ¿Detalle seguro? | **SÍ** — `SELECT` por id + RLS |
| 5 | ¿RPC compat `photo_url`? | **NO** escribe `photo_url`; `m08_set_pet_avatar_asset` solo `avatar_file_asset_id` |
| 6 | ¿Legacy interface sin doble escritura? | **SÍ** con adaptador, **si** existen RPC create/update/archive/avatar |
| 7 | ¿Create devuelve datos para `Pet`? | **Parcial** — retorna row; ages/breed/health no pasan por create RPC (defaults) |
| 8 | ¿Tipos retorno serializables? | **Con DTO actualizado sí**; con `PetRow` actual **NO** |
| 9 | ¿Errores distinguibles? | **SÍ** (códigos texto) |
| 10 | ¿Etapa 4 completa sin SQL adicional? | **NO** |

---

## 6. Contratos SQL faltantes (bloqueantes)

Clasificación: **CONTRATO SQL FALTANTE / BLOQUEANTE**

1. **`m08_update_pet_profile`** (o equivalente) — name, species, sex, size, description, ages, breed/color/etc. con `pet.update`.  
2. **`m08_update_pet_health`** (o mismo RPC con sección health) — vaccinations, deworming, flea, sterilized, microchip_id, last_vet_visit, health_notes, weight, reminders.  
3. (Recomendado no bloqueante) create enriquecido o update inmediato post-create para alinear formulario completo.  
4. (Opcional) `m08_list_accessible_pets` — no obligatorio si SELECT+RLS.

**Prohibido:** reactivar UPDATE directo authenticated; no editar 035; no GUC falsificable.

---

## 7. Estrategia de compatibilidad (freeze para 4B, post-3C)

1. **Etapa 3C** — migración **036** forward-fix con RPC update (+ grants/search_path/revoke PUBLIC). Validación local.  
2. **Etapa 4B** — DTO RPC, mappers, `SupabasePetDomainRepository`, `LegacyPetRepositoryAdapter` implementando `PetRepository` **solo** vía RPC + SELECT RLS.  
3. **Sin doble escritura.**  
4. **owner_id persona:** proyección RPC; **org:** `ownerId` null en modelo.  
5. **Listados:** accesibles (RLS), no `eq(owner_id)`.  
6. **Delete UI →** `m08_archive_pet`.  
7. **Avatar:** M05 upload → `m08_set_pet_avatar_asset`; `photo_url` solo fallback lectura.  
8. **Feature flag:** no requerido si 036+Android se aplican en orden staging: primero SQL, luego app que solo usa RPC.  
9. **Rollback cliente:** revert app a build pre-RPC solo si staging SQL se revierte (no recomendado).  
10. **Orden staging:** 036 local PASS → Android 4B PASS → apply 035+036 staging → smoke.

---

## 8. DTO / mappers / errores faltantes (Android 4B)

- `PetRow` + columnas M08; `ownerId: String?`  
- Params DTOs por RPC  
- `PetRpcErrorMapper`  
- Mapper `pets` row → `data.model.Pet` y → `PetAggregate`  
- Adapter `PetRepository` → domain/RPC  

---

## 9. DataProvider

Tras 4B: `petRepository` = adapter M08 cuando backend ≥ 036. Mock permanece para tests offline.

---

## 10. Orden de implementación

1. **3C** — 036 update RPC + matriz SQL + quality.  
2. **4B** — DTOs/mappers/adapter/tests.  
3. Ajustes ViewModel mínimos (listado, canManage caps, archive) — **fuera** de 4A; en 4B sin rediseño UI.  
4. Autorizar staging SQL solo con Android listo.

---

## 11. Riesgos

| Riesgo | Nivel |
|---|---|
| Apply 035 sin 036/Android | Crítico — CRUD roto |
| Decodificar `owner_id` null | Alto |
| Listados por owner | Alto |
| photo_url = assetId | Medio |
| Create sin ages/health en un solo RPC | Medio (mitigable con update 036) |

---

## 12. Conclusión

```text
M08 ETAPA 4A — BLOQUEADA POR CONTRATOS SQL FALTANTES
REQUIERE ETAPA 3C — FORWARD-FIX 036
STAGING NO AUTORIZADO
```

**Siguiente paso:** Etapa **3C** — diseñar e implementar migración **036** (`m08_update_pet_*`) y validarla localmente; **después** Etapa 4B.
