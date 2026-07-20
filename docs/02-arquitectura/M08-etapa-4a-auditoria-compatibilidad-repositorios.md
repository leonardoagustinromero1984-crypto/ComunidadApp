# M08 Etapa 4A â€” Cierre formal: bloqueo por contratos SQL faltantes

**Producto:** LeoVer
**Rama:** `m08/etapa-4a-auditoria-compatibilidad-repositorios`
**Tipo:** cierre documental histÃ³rico (auditorÃ­a que motivÃ³ 3C)
**Base 3B:** `e31c838`
**Main integrado:** `ebd9487` (PR #2) vÃ­a merge `3ee0829`

```text
M08 ETAPA 4A â€” CERRADA (auditorÃ­a)
RESOLUCIÃ“N SQL: Etapa 3C â€” migraciÃ³n 036 (forward-fix local)
STAGING NO AUTORIZADO
REQUIERE ETAPA 4B â€” REPOSITORIOS Y ADAPTADOR LEGACY
```

**Estado post-3C:** contratos Aâ€“C + listado accesible implementados en `036_m08_pet_repository_compatibility_rpcs.sql`. Perfil pÃºblico permanece oculto (sin `m08_list_public_profile_pets`). Etapa 4B puede comenzar sobre 036 local validada; staging sigue bloqueado.

---

## 1. Base integrada

| Ref | Valor |
|---|---|
| Cierre 3B | `e31c838` â€” 035 validada local |
| CI | `a705f25`, `7105a11` â†’ `ebd9487` |
| Highest tras 3C | **036** |
| Archivo 036 | **creado en Etapa 3C** |
| 035 modificado | **NO** |
| Android / UI | **NO modificado** (hasta 4B) |
| Supabase remoto | **NO** |

AuditorÃ­a runtime Android ya realizada (no repetir shells 418859/418860). Hallazgos confirmados abajo.

---

## 2. Hallazgos confirmados (resumen ejecutivo)

| # | Hallazgo | Impacto |
|---|---|---|
| 1 | `createPet` INSERT directo bloqueado; cubierto por `m08_create_pet_with_principal` | REQUIERE ADAPTADOR |
| 2 | `updatePet` UPDATE directo bloqueado; **sin RPC** para bÃ¡sicos/salud/microchip/photo | **BLOQUEANTE** |
| 3 | `deletePet` DELETE bloqueado â†’ `m08_archive_pet`; deceased separado | REQUIERE ADAPTADOR |
| 4 | Avatar vÃ­a `updatePet(photo_url)` bloqueado â†’ M05 + `m08_set_pet_avatar_asset` | REQUIERE ADAPTADOR |
| 5 | Listados por `owner_id` incompletos (co-resp / org null) | REQUIERE ADAPTADOR |
| 6 | `canManage = ownerId == uid` obsoleto â†’ capabilities | REQUIERE ADAPTADOR + contexto |
| 7 | `owner_id` NULL vs Kotlin `String` non-null | REQUIERE ADAPTADOR / modelo |
| 8 | Perfil pÃºblico: fin SELECT-all â†’ sin mascotas visibles | **DECISIÃ“N PENDIENTE BLOQUEANTE PARA PERFIL PÃšBLICO** |
| 9 | Todas las `m08_*` sin DTO/impl Android | REQUIERE ADAPTADOR (post-3C) |

---

## 3. Contratos SQL faltantes (congelados para 036)

ClasificaciÃ³n: **CONTRATO SQL FALTANTE**.

### A. `m08_update_pet_profile`

**Cap:** `pet.update`
**MÃ­nimo:** name, species, breed, sex, size, description, microchip (`microchip_id`); ages (`age_years`/`age_months`) â€” el esquema actual **no** tiene `birth_date`; no inventar columna en 036 salvo decisiÃ³n explÃ­cita aparte.
**Debe:** normalizar microchip (`m08_normalize_microchip`), respetar soft-unique ACTIVE, no tocar `owner_id` ni status lifecycle.
**Retorno:** `public.pets`.
**Errores:** `FORBIDDEN`, `PET_NOT_FOUND`, `PET_NOT_ACTIVE`, `PET_MICROCHIP_ACTIVE_CONFLICT`, etc.

### B. `m08_update_pet_health`

**Cap:** `pet.manage_health`
**Campos:** Ãºnicamente salud legacy existentes (vaccinations, deworming*, flea*, sterilized, last_vet_visit, health_notes, weight_kg, reminders).
**No** mezclar con perfil bÃ¡sico. Microchip queda en profile RPC (identidad), no en health, salvo que 3C documente excepciÃ³n justificada.

### C. `m08_get_pet_access_context`

**Entrada:** `p_pet_id`
**Salida mÃ­nima:**

- `pet_id`
- relaciÃ³n del actor (PRINCIPAL / CO_RESPONSIBLE / TEMPORARY_CUSTODIAN / AUTHORIZED / STAFF / NONE)
- principal person_id / organization_id
- capabilities efectivas (`text[]`)
- flags: `can_read`, `can_update`, `can_manage_health`, `can_manage_media`, `can_manage_responsibilities`, `can_transfer`, `can_archive`, `can_mark_deceased`

**Prohibido:** confiar en `owner_id == auth.uid()` en cliente.

### D. Perfil pÃºblico â€” decisiÃ³n pendiente bloqueante

Alternativas:

1. **No mostrar mascotas** en perfil pÃºblico tras 035 (solo propias / relaciÃ³n que ya ve contenido).
2. **`m08_list_public_profile_pets(p_profile_user_id)`** â€” proyecciÃ³n segura (id, name, photo/avatar, species; sin salud/microchip) respetando privacidad del perfil (`ProfilePrivacy` / `profile_private`).

**Sin** polÃ­tica SELECT-all.

**RecomendaciÃ³n (privacidad LeoVer):** opciÃ³n **1** por defecto en 4B/staging inicial â€” ocultar mascotas ajenas en perfil pÃºblico hasta definir proyecciÃ³n; alinear con `filterVisiblePets` que ya exige viewer autenticado y `canViewUserContent`. Si producto exige vitrina pÃºblica, entonces opciÃ³n **2** entra en alcance 036.

### E. Listado accesible

- **SELECT** `pets` bajo RLS (`m08_actor_can_read_pet`) **es suficiente** para IDs/filas legibles.
- **No** alcanza para devolver relaciÃ³n/capabilities â†’ hace falta **`m08_get_pet_access_context`** (por mascota) y, si MyPets necesita relaciÃ³n en batch: **`m08_list_accessible_pets`** (RPC o vista segura) **incluida en alcance 036**.

DecisiÃ³n congelada: **incluir `m08_list_accessible_pets` en 036** (evita N+1 de access_context en lista).

---

## 4. Alcance propuesto migraciÃ³n 036 (NO crear archivo aÃºn)

Forward-fix; **no** reescribir 035.

| Incluir | Notas |
|---|---|
| `m08_update_pet_profile` | Obligatorio |
| `m08_update_pet_health` | Obligatorio |
| `m08_get_pet_access_context` | Obligatorio |
| `m08_list_accessible_pets` | Obligatorio (batch MyPets) |
| `m08_list_public_profile_pets` | Solo si se aprueba opciÃ³n 2 de perfil |
| Grants / search_path / REVOKE PUBLIC | PatrÃ³n M08 |
| DEFINER solo donde necesario | Como 035 |
| Helpers RLS si hacen falta | Sin debilitar |
| Eventos M07 `m08.pet.updated` (y keys nuevas si aplica) | Idempotente catalog |
| Script validaciÃ³n SQL local | Extender matriz M08 |
| Sin DROP destructivo / sin hard-delete history | |

---

## 5. Estrategia post-3C (referencia 4B â€” no iniciar)

1. Adapter `PetRepository` â†’ RPC + SELECT RLS; sin doble escritura.
2. `ownerId: String?`; status/avatar en DTO.
3. Delete UI â†’ archive.
4. Avatar M05 â†’ `m08_set_pet_avatar_asset`.
5. canManage â†’ access_context / capabilities.
6. Staging solo tras 035+036 locales PASS + Android 4B PASS.

---

## 6. ConclusiÃ³n

```text
M08 ETAPA 4A â€” BLOQUEADA POR CONTRATOS SQL FALTANTES
REQUIERE ETAPA 3C â€” FORWARD-FIX 036
STAGING NO AUTORIZADO
```

**Siguiente paso exacto:** abrir Etapa **3C** (diseÃ±o + SQL 036 + validaciÃ³n local). **No** Etapa 4B. **No** apply remoto 035.
