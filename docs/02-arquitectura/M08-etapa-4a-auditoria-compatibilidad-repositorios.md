# M08 Etapa 4A — Cierre formal: bloqueo por contratos SQL faltantes

**Producto:** LeoVer
**Rama:** `m08/etapa-4a-auditoria-compatibilidad-repositorios`
**Tipo:** cierre documental (sin implementación Android ni SQL 036)
**Base 3B:** `e31c838`
**Main integrado:** `ebd9487` (PR #2) vía merge `3ee0829`

```text
M08 ETAPA 4A — BLOQUEADA POR CONTRATOS SQL FALTANTES
REQUIERE ETAPA 3C — FORWARD-FIX 036
STAGING NO AUTORIZADO
```

**Etapa 4B no puede comenzar** hasta cerrar 3C.

---

## 1. Base integrada

| Ref | Valor |
|---|---|
| Cierre 3B | `e31c838` — 035 validada local |
| CI | `a705f25`, `7105a11` → `ebd9487` |
| Highest migration | **035** |
| Archivo 036 | **NO creado** |
| 035 modificado | **NO** |
| Android / UI | **NO modificado** |
| Supabase remoto | **NO** |

Auditoría runtime Android ya realizada (no repetir shells 418859/418860). Hallazgos confirmados abajo.

---

## 2. Hallazgos confirmados (resumen ejecutivo)

| # | Hallazgo | Impacto |
|---|---|---|
| 1 | `createPet` INSERT directo bloqueado; cubierto por `m08_create_pet_with_principal` | REQUIERE ADAPTADOR |
| 2 | `updatePet` UPDATE directo bloqueado; **sin RPC** para básicos/salud/microchip/photo | **BLOQUEANTE** |
| 3 | `deletePet` DELETE bloqueado → `m08_archive_pet`; deceased separado | REQUIERE ADAPTADOR |
| 4 | Avatar vía `updatePet(photo_url)` bloqueado → M05 + `m08_set_pet_avatar_asset` | REQUIERE ADAPTADOR |
| 5 | Listados por `owner_id` incompletos (co-resp / org null) | REQUIERE ADAPTADOR |
| 6 | `canManage = ownerId == uid` obsoleto → capabilities | REQUIERE ADAPTADOR + contexto |
| 7 | `owner_id` NULL vs Kotlin `String` non-null | REQUIERE ADAPTADOR / modelo |
| 8 | Perfil público: fin SELECT-all → sin mascotas visibles | **DECISIÓN PENDIENTE BLOQUEANTE PARA PERFIL PÚBLICO** |
| 9 | Todas las `m08_*` sin DTO/impl Android | REQUIERE ADAPTADOR (post-3C) |

---

## 3. Contratos SQL faltantes (congelados para 036)

Clasificación: **CONTRATO SQL FALTANTE**.

### A. `m08_update_pet_profile`

**Cap:** `pet.update`
**Mínimo:** name, species, breed, sex, size, description, microchip (`microchip_id`); ages (`age_years`/`age_months`) — el esquema actual **no** tiene `birth_date`; no inventar columna en 036 salvo decisión explícita aparte.
**Debe:** normalizar microchip (`m08_normalize_microchip`), respetar soft-unique ACTIVE, no tocar `owner_id` ni status lifecycle.
**Retorno:** `public.pets`.
**Errores:** `FORBIDDEN`, `PET_NOT_FOUND`, `PET_NOT_ACTIVE`, `PET_MICROCHIP_ACTIVE_CONFLICT`, etc.

### B. `m08_update_pet_health`

**Cap:** `pet.manage_health`
**Campos:** únicamente salud legacy existentes (vaccinations, deworming*, flea*, sterilized, last_vet_visit, health_notes, weight_kg, reminders).
**No** mezclar con perfil básico. Microchip queda en profile RPC (identidad), no en health, salvo que 3C documente excepción justificada.

### C. `m08_get_pet_access_context`

**Entrada:** `p_pet_id`
**Salida mínima:**

- `pet_id`
- relación del actor (PRINCIPAL / CO_RESPONSIBLE / TEMPORARY_CUSTODIAN / AUTHORIZED / STAFF / NONE)
- principal person_id / organization_id
- capabilities efectivas (`text[]`)
- flags: `can_read`, `can_update`, `can_manage_health`, `can_manage_media`, `can_manage_responsibilities`, `can_transfer`, `can_archive`, `can_mark_deceased`

**Prohibido:** confiar en `owner_id == auth.uid()` en cliente.

### D. Perfil público — decisión pendiente bloqueante

Alternativas:

1. **No mostrar mascotas** en perfil público tras 035 (solo propias / relación que ya ve contenido).
2. **`m08_list_public_profile_pets(p_profile_user_id)`** — proyección segura (id, name, photo/avatar, species; sin salud/microchip) respetando privacidad del perfil (`ProfilePrivacy` / `profile_private`).

**Sin** política SELECT-all.

**Recomendación (privacidad LeoVer):** opción **1** por defecto en 4B/staging inicial — ocultar mascotas ajenas en perfil público hasta definir proyección; alinear con `filterVisiblePets` que ya exige viewer autenticado y `canViewUserContent`. Si producto exige vitrina pública, entonces opción **2** entra en alcance 036.

### E. Listado accesible

- **SELECT** `pets` bajo RLS (`m08_actor_can_read_pet`) **es suficiente** para IDs/filas legibles.
- **No** alcanza para devolver relación/capabilities → hace falta **`m08_get_pet_access_context`** (por mascota) y, si MyPets necesita relación en batch: **`m08_list_accessible_pets`** (RPC o vista segura) **incluida en alcance 036**.

Decisión congelada: **incluir `m08_list_accessible_pets` en 036** (evita N+1 de access_context en lista).

---

## 4. Alcance propuesto migración 036 (NO crear archivo aún)

Forward-fix; **no** reescribir 035.

| Incluir | Notas |
|---|---|
| `m08_update_pet_profile` | Obligatorio |
| `m08_update_pet_health` | Obligatorio |
| `m08_get_pet_access_context` | Obligatorio |
| `m08_list_accessible_pets` | Obligatorio (batch MyPets) |
| `m08_list_public_profile_pets` | Solo si se aprueba opción 2 de perfil |
| Grants / search_path / REVOKE PUBLIC | Patrón M08 |
| DEFINER solo donde necesario | Como 035 |
| Helpers RLS si hacen falta | Sin debilitar |
| Eventos M07 `m08.pet.updated` (y keys nuevas si aplica) | Idempotente catalog |
| Script validación SQL local | Extender matriz M08 |
| Sin DROP destructivo / sin hard-delete history | |

---

## 5. Estrategia post-3C (referencia 4B — no iniciar)

1. Adapter `PetRepository` → RPC + SELECT RLS; sin doble escritura.
2. `ownerId: String?`; status/avatar en DTO.
3. Delete UI → archive.
4. Avatar M05 → `m08_set_pet_avatar_asset`.
5. canManage → access_context / capabilities.
6. Staging solo tras 035+036 locales PASS + Android 4B PASS.

---

## 6. Conclusión

```text
M08 ETAPA 4A — BLOQUEADA POR CONTRATOS SQL FALTANTES
REQUIERE ETAPA 3C — FORWARD-FIX 036
STAGING NO AUTORIZADO
```

**Siguiente paso exacto:** abrir Etapa **3C** (diseño + SQL 036 + validación local). **No** Etapa 4B. **No** apply remoto 035.
